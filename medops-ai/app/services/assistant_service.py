"""Assistant chat orchestration: user message → LLM → validated reply.

The assistant has NO access to patient records in this phase. It must never
invent appointments, reports, prescriptions, bills, or clinical findings.
"""

from __future__ import annotations

import logging
import re

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
    "You do not have access to any user's records in this version — never invent "
    "appointments, reports, prescriptions, bills, or clinical findings. "
    "When asked for personal information you cannot see, say so and point the user "
    "to the relevant MedOps section instead. "
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


class AssistantService:
    """Isolates the assistant prompt/output rules from FastAPI routes."""

    def __init__(self, client: ChatClient | None = None) -> None:
        """Initialise with an explicit client or the auto-detected one."""
        self._client = client if client is not None else build_chat_client()

    async def chat(self, message: str) -> str:
        """Produce an assistant reply for a user's chat message.

        When no LLM backend is configured, returns a deterministic stub string so
        the endpoint remains usable for smoke tests.

        :param message: the validated, non-blank user message
        :returns: raw reply text (never ``None``)
        """
        if self._client is None:
            return STUB_REPLY

        result: ChatResult = await self._client.chat(
            system=SYSTEM_PROMPT,
            user=message,
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