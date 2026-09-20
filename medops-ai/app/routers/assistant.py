"""Assistant chat endpoints.

Spring Boot owns authentication and business data. The API attaches only a
bounded, read-only snapshot of the signed-in user's OWN upcoming appointments;
this service turns that into a safety-checked LLM reply. No patient identifiers
are accepted or returned.
"""

from __future__ import annotations

import logging
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.assistant_service import (
    MAX_CONTEXT_APPOINTMENTS,
    AssistantAppointmentContext,
    AssistantService,
)
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
        description="IANA time zone the appointment times were rendered in",
    )
    appointments: List[AssistantAppointmentContext] = Field(
        default_factory=list,
        max_length=MAX_CONTEXT_APPOINTMENTS,
        description="Read-only snapshot of the user's own upcoming appointments",
    )


class AssistantChatResponse(BaseModel):
    """Response payload containing the assistant's reply.

    :param message: the AI-generated reply text.
    """

    message: str


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
        raw = await assistant_service.chat(
            body.message,
            appointments=body.appointments,
            time_zone=body.time_zone,
        )
        reply = assistant_service.validate_reply(raw)
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

    return AssistantChatResponse(message=reply)