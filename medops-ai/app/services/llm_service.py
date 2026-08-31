"""LLM inference orchestration.

Route → LLMService → chat client (API dialect) → upstream.
PDF text is extracted locally; model output is validated before return.
"""

from __future__ import annotations

import io
import logging
import re
from typing import Protocol

from pypdf import PdfReader

from app.config import (
    PROVIDER_CHAT_COMPLETIONS,
    PROVIDER_GENERATE_CONTENT,
    settings,
)
from app.services.chat_completions_client import ChatCompletionsClient
from app.services.generate_content_client import GenerateContentClient
from app.services.llm_types import ChatResult, LlmError, LlmResponseError

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "You are a clinical documentation assistant for MedOps. "
    "Write a short plain-language overview of the lab/report text. "
    "Do not diagnose, prescribe, or invent findings that are not in the text. "
    "If the extract is incomplete, say what is missing instead of guessing. "
    "This is not medical advice."
)

_BLOCKED_CLAIM = re.compile(
    r"\b(you (have|are diagnosed with)|i diagnose|prescribe|start taking)\b",
    re.IGNORECASE,
)


class ChatClient(Protocol):
    async def chat(
        self,
        *,
        system: str,
        user: str,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> ChatResult: ...


def build_chat_client() -> ChatClient | None:
    provider = settings.resolved_provider()
    if provider == PROVIDER_GENERATE_CONTENT:
        return GenerateContentClient()
    if provider == PROVIDER_CHAT_COMPLETIONS:
        return ChatCompletionsClient()
    return None


class LLMService:
    """Isolates provider choice (Strategy/Adapter) from FastAPI routes."""

    def __init__(self, client: ChatClient | None = None) -> None:
        self._client = client if client is not None else build_chat_client()

    async def summarize(self, report_id: str, pdf_bytes: bytes) -> str:
        if self._client is None:
            pages_estimate = max(1, len(pdf_bytes) // 50_000)
            return (
                "Plain-language overview (AI service stub): this lab PDF is about "
                f"{pages_estimate} page(s). This is not a diagnosis or treatment advice. "
                f"report_id={report_id}"
            )

        extract = extract_pdf_text(pdf_bytes)
        user_prompt = (
            f"Report id: {report_id}\n"
            f"Extracted text (may be truncated):\n"
            f"{extract if extract else '[no extractable text — PDF may be scanned/image-only]'}"
        )

        result = await self._client.chat(system=SYSTEM_PROMPT, user=user_prompt)
        return result.content

    def validate_summary(self, summary: str) -> str:
        """Probabilistic output must pass business rules before leaving the service."""
        text = (summary or "").strip()
        if not text:
            raise ValueError("Empty summarizer response")
        if len(text) > 4_000:
            raise ValueError("Summary exceeds length limit")
        if _BLOCKED_CLAIM.search(text):
            raise ValueError("Summary contains disallowed diagnostic/prescriptive claims")
        return text


def extract_pdf_text(pdf_bytes: bytes) -> str:
    try:
        reader = PdfReader(io.BytesIO(pdf_bytes))
        parts: list[str] = []
        for page in reader.pages:
            page_text = page.extract_text() or ""
            if page_text.strip():
                parts.append(page_text.strip())
        joined = "\n\n".join(parts).strip()
        if len(joined) > settings.max_pdf_chars:
            logger.info(
                "pdf_text_truncated chars=%s max=%s",
                len(joined),
                settings.max_pdf_chars,
            )
            return joined[: settings.max_pdf_chars]
        return joined
    except Exception:  # noqa: BLE001 — treat corrupt PDF extract as empty
        logger.warning("pdf_text_extract_failed")
        return ""


__all__ = ["LLMService", "LlmError", "LlmResponseError", "extract_pdf_text"]
