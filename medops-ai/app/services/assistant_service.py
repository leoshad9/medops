"""Assistant chat orchestration: user message + appointment snapshot → LLM → validated reply.

The Spring Boot API may attach a bounded, read-only snapshot of the signed-in
user's OWN upcoming appointments. The assistant must answer scheduling
questions only from that snapshot and must never invent appointments, reports,
prescriptions, bills, or clinical findings.
"""

from __future__ import annotations

import logging
import re
from typing import Optional, Sequence

from pydantic import BaseModel, Field

from app.config import settings
from app.services.llm_service import ChatClient, build_chat_client
from app.services.llm_types import ChatResult

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "You are the MedOps AI Assistant for the MedOps patient portal. "
    "Help users navigate MedOps and answer general questions about appointments, "
    "prescriptions, lab reports, billing, and using the portal. "
    "You are not a doctor and must not diagnose conditions, prescribe treatment, "
    "or give medical advice. "
    "When a read-only snapshot of the signed-in user's own upcoming appointments "
    "is provided, treat it as authoritative and answer scheduling questions only "
    "from it. Never invent appointments, reports, prescriptions, bills, or "
    "clinical findings beyond that snapshot. "
    "When asked for personal information that is not in the snapshot, or that is "
    "not provided at all, say you cannot see it and point the user to the "
    "relevant MedOps section instead. "
    "Use plain language and stay concise and helpful. This is not medical advice."
)

# Rejects diagnostic/prescriptive *advice* in the reply. Worded narrowly so
# legitimate navigation help ("you can request refills in Prescriptions") passes.
_BLOCKED_CLAIM = re.compile(
    r"\b(i diagnose|you are diagnosed with|you (?:may|might) have|"
    r"you should (?:take|stop taking)|you need to take)\b",
    re.IGNORECASE,
)

MAX_REPLY_CHARS = 4_000

STUB_REPLY = (
    "I'm the MedOps AI Assistant (AI service stub). I can help with appointments, "
    "reports, prescriptions, billing, and using MedOps."
)

MAX_CONTEXT_APPOINTMENTS = 10
_MAX_CONTEXT_FIELD_CHARS = 200


class AssistantAppointmentContext(BaseModel):
    """LLM-safe projection of one of the user's own upcoming appointments.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers, MRNs, and free-text visit reasons are never included.

    :param starts_at_local: start time already rendered in the user's zone.
    :param status: appointment status such as ``BOOKED``.
    :param practitioner_name: doctor's display name, if known.
    :param specialty: doctor's specialty, if known.
    :param location: clinic or room text, if known.
    """

    starts_at_local: str = Field(..., max_length=64)
    status: str = Field(..., max_length=32)
    practitioner_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    specialty: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    location: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)


def _sanitize_field(value: Optional[str]) -> Optional[str]:
    """Collapse whitespace so record text cannot smuggle prompt instructions."""
    if value is None:
        return None
    collapsed = " ".join(value.split())
    return collapsed[:_MAX_CONTEXT_FIELD_CHARS] or None


def _appointment_snapshot(
    appointments: Sequence[AssistantAppointmentContext], time_zone: Optional[str]
) -> str:
    """Render the API-provided appointment snapshot as a prompt section."""
    zone = _sanitize_field(time_zone) or "the user's local time zone"
    lines = []
    for item in appointments[:MAX_CONTEXT_APPOINTMENTS]:
        parts = [f"- {_sanitize_field(item.starts_at_local)}"]
        practitioner = _sanitize_field(item.practitioner_name)
        specialty = _sanitize_field(item.specialty)
        location = _sanitize_field(item.location)
        if practitioner:
            parts.append(f"with {practitioner}")
        if specialty:
            parts.append(f"({specialty})")
        if location:
            parts.append(f"at {location}")
        parts.append(f"[{_sanitize_field(item.status)}]")
        lines.append(" ".join(parts))
    return (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own upcoming appointments "
        f"(read-only, already shown in the user's local time zone: {zone}). "
        "Answer appointment questions using only these entries; if a requested "
        "detail is not listed, say you cannot see it.\n" + "\n".join(lines)
    )


class AssistantService:
    """Isolates the assistant prompt/output rules from FastAPI routes."""

    def __init__(self, client: ChatClient | None = None) -> None:
        """Initialise with an explicit client or the auto-detected one."""
        self._client = client if client is not None else build_chat_client()

    async def chat(
        self,
        message: str,
        appointments: Optional[Sequence[AssistantAppointmentContext]] = None,
        time_zone: Optional[str] = None,
    ) -> str:
        """Produce an assistant reply for a user's chat message.

        When no LLM backend is configured, returns a deterministic stub string so
        the endpoint remains usable for smoke tests.

        :param message: the validated, non-blank user message
        :param appointments: optional read-only snapshot of the user's own
            upcoming appointments, assembled by the API for this user only
        :param time_zone: optional IANA zone the snapshot times were rendered in
        :returns: raw reply text (never ``None``)
        """
        if self._client is None:
            return STUB_REPLY

        user_prompt = message
        if appointments:
            snapshot = _appointment_snapshot(appointments, time_zone)
            user_prompt = f"{snapshot}\n\nUser question: {message}"

        result: ChatResult = await self._client.chat(
            system=SYSTEM_PROMPT,
            user=user_prompt,
            temperature=settings.llm_temperature,
            max_tokens=settings.llm_max_tokens,
        )
        return result.content

    def validate_reply(self, reply: str) -> str:
        """Probabilistic output must pass business rules before leaving the service."""
        text = (reply or "").strip()
        if not text:
            raise ValueError("Empty assistant response")
        if len(text) > MAX_REPLY_CHARS:
            raise ValueError("Assistant response exceeds length limit")
        if _BLOCKED_CLAIM.search(text):
            raise ValueError("Assistant response contains disallowed diagnostic/prescriptive claims")
        return text