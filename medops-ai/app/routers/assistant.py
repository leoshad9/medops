"""Assistant chat endpoints.

Spring Boot owns authentication and business data. The API attaches only a
bounded, read-only snapshot of the signed-in user's OWN appointments, lab
reports, prescriptions, invoices, and medical records; this service turns that
into a safety-checked LLM reply. No patient identifiers are accepted or returned.
"""

from __future__ import annotations

import logging
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.assistant_service import (
    MAX_CONTEXT_APPOINTMENTS,
    MAX_CONTEXT_ITEMS,
    AssistantContext,
    AssistantAppointmentContext,
    AssistantInvoiceContext,
    AssistantLabReportContext,
    AssistantMedicalRecordContext,
    AssistantPrescriptionContext,
    AssistantService,
)
from app.utils import metrics
from app.services.llm_types import (
    LlmError,
    LlmRateLimitError,
    LlmTimeoutError,
    LlmUnavailableError,
)

router = APIRouter(prefix="/ai", tags=["assistant"])

logger = logging.getLogger(__name__)

assistant_service = AssistantService()

MAX_MESSAGE_CHARS = 2000


class AssistantChatRequest(BaseModel):
    """Request payload for the MedOps AI assistant chat.

    :param message: the user's chat message (1–2000 characters).
    :param time_zone: optional IANA time zone of the user's browser session.
    :param appointments: bounded, read-only snapshot of the signed-in user's
        own upcoming appointments, assembled server-side by the Spring Boot API.
    :param lab_reports: bounded, read-only snapshot of the user's own lab reports.
    :param prescriptions: bounded, read-only snapshot of the user's own prescriptions.
    :param invoices: bounded, read-only snapshot of the user's own invoices.
    :param medical_records: bounded, read-only snapshot of the user's own
        uploaded medical records.
    """

    message: str = Field(
        ...,
        min_length=1,
        max_length=MAX_MESSAGE_CHARS,
        description="User chat message",
    )
    time_zone: Optional[str] = Field(
        default=None,
        max_length=64,
        description="IANA time zone the record times were rendered in",
    )
    appointments: List[AssistantAppointmentContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_APPOINTMENTS,
        description="Read-only snapshot of the user's own upcoming appointments",
    )
    lab_reports: List[AssistantLabReportContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_ITEMS,
        description="Read-only snapshot of the user's own lab reports",
    )
    prescriptions: List[AssistantPrescriptionContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_ITEMS,
        description="Read-only snapshot of the user's own prescriptions",
    )
    invoices: List[AssistantInvoiceContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_ITEMS,
        description="Read-only snapshot of the user's own invoices",
    )
    medical_records: List[AssistantMedicalRecordContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_ITEMS,
        description="Read-only snapshot of the user's own medical records",
    )


class AssistantChatResponse(BaseModel):
    """Response payload containing the assistant's reply.

    :param message: the AI-generated reply text.
    :param actions: optional resolved action payloads.
    :param suggestions: optional follow-up suggestions surfaced by the model.
    """

    message: str
    # Optional resolved action payloads the server computed for any actions
    # included in the assistant's JSON block. Each entry matches the
    # assistant-suggested action `id` and a `resolved` payload or null.
    actions: Optional[List[dict]] = None
    suggestions: Optional[List[str]] = None


def _resolve_action_payloads(service, context: AssistantContext, actions_block: dict) -> Optional[List[dict]]:
    """Resolve allowed JSON action ids into the safe server-side payloads."""
    actions = actions_block.get("actions", [])
    if not isinstance(actions, list):
        return None

    resolved: List[dict] = []
    for action in actions:
        if not isinstance(action, dict):
            continue
        aid = action.get("id")
        if not aid:
            resolved.append({"id": None, "resolved": None})
            continue

        payload = None
        if hasattr(service, "resolve_action"):
            try:
                payload = service.resolve_action(aid, context)
            except Exception:
                payload = None

        resolved.append({
            "id": aid,
            "resolved": payload,
            "label": action.get("label") if isinstance(action.get("label"), str) else None,
        })

    return resolved or None


def _extract_suggestions(actions_block: dict) -> Optional[List[str]]:
    """Normalize model-provided suggestions into a simple string list."""
    raw_suggestions = actions_block.get("suggestions")
    if not isinstance(raw_suggestions, list):
        return None

    suggestions: List[str] = []
    for item in raw_suggestions:
        if isinstance(item, str):
            suggestions.append(item)
            continue
        if not isinstance(item, dict):
            continue

        label = item.get("label")
        query = item.get("query")
        if isinstance(label, str):
            suggestions.append(label)
        elif isinstance(query, str):
            suggestions.append(query)

    return suggestions or None


def _extract_response_metadata(service, context: AssistantContext, reply: str):
    """Extract any structured action payloads and suggestions from the model reply."""
    if not hasattr(service, "_extract_actions_block"):
        return None, None

    actions_block = service._extract_actions_block(reply)
    if not isinstance(actions_block, dict):
        return None, None

    resolved = _resolve_action_payloads(service, context, actions_block)
    suggestions = _extract_suggestions(actions_block)
    return resolved, suggestions


@router.post("/assistant/chat", response_model=AssistantChatResponse)
async def assistant_chat(body: AssistantChatRequest):
    """Answer a user's chat message using the configured LLM backend.

    Provider-specific errors (timeouts, rate limits, unavailable errors) are
    mapped to appropriate HTTP status codes; exception details are logged
    server-side only. User messages and completions are never logged.

    :param body: request containing the user's chat message.
    :returns: the validated assistant reply.
    """
    if not body.message.strip():
        raise HTTPException(status_code=400, detail="Message must not be blank")

    # Do not log the user message or the completion.
    logger.info("assistant_chat accepted chars=%s", len(body.message))

    try:
        context = AssistantContext(
            appointments=body.appointments,
            lab_reports=body.lab_reports,
            prescriptions=body.prescriptions,
            invoices=body.invoices,
            medical_records=body.medical_records,
        )
        raw = await assistant_service.chat(body.message, context, body.time_zone)
        # increment simple assistant request counter
        try:
            metrics.increment("assistant_chat_requests")
        except Exception:
            logger.exception("Failed to increment assistant metrics")
        reply = assistant_service.validate_reply(raw)
        try:
            resolved, suggestions = _extract_response_metadata(assistant_service, context, reply)
        except Exception:
            # resolution failures must not expose internals; log and continue
            logger.exception("Failed to resolve assistant actions")
            resolved, suggestions = None, None
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="Assistant returned an unusable result") from exc
    except LlmTimeoutError as exc:
        raise HTTPException(status_code=504, detail="LLM request timed out") from exc
    except LlmRateLimitError as exc:
        logger.warning("LLM rate limit for assistant chat: %s", exc)
        raise HTTPException(
            status_code=429,
            detail="LLM provider rate limit exceeded. Please try again shortly.",
        ) from exc
    except LlmUnavailableError as exc:
        raise HTTPException(status_code=503, detail="LLM provider unavailable") from exc
    except LlmError as exc:
        raise HTTPException(status_code=502, detail="Assistant chat failed") from exc

    payload = {"message": reply, "actions": resolved}
    if suggestions is not None:
        payload["suggestions"] = suggestions
    return payload