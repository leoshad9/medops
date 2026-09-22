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
    "Write a concise, plain-language summary of the lab/report text for display in a small web UI card. "
    "Begin the summary with the exact label 'Summary:' on its own line, then a blank line, "
    "then 3-5 short bullets of one to two lines each. "
    "Use light Markdown: bold section labels (e.g. **CBC:**, **Glucose:**) and a bullet list. "
    "Do NOT write sentence introductions like 'Here is a summary of ...'. "
    "Do NOT emit raw HTML. Do NOT use # markdown headers. "
    "Report only what is in the text; do not diagnose, prescribe, or invent findings. "
    "If the extract is incomplete, say what is missing instead of guessing. "
    "This is not medical advice."
)

_BLOCKED_CLAIM = re.compile(
    r"\b(you (have|are diagnosed with)|i diagnose|prescribe|start taking)\b",
    re.IGNORECASE,
)

_SUMMARY_LABEL = "Summary:"


def _strip_markdown_markers(text: str) -> str:
    """Strip leading heading/list markers and surrounding bold markers for label comparison."""
    stripped = text.strip().lstrip("#").strip()
    if stripped.startswith(("- ", "* ", "+ ")):
        stripped = stripped[2:]
    # Use str strip methods (not a regex) to avoid polynomial-redos on untrusted input.
    stripped = stripped.lstrip("*").rstrip("*").strip()
    return stripped


def normalize_summary_intro(summary: str) -> str:
    """Ensure the summary begins with the canonical ``Summary:`` label.

    Drops verbose leading headings (e.g. ``Here is a summary of ...:``) and
    guarantees a plain-text ``Summary:`` label on the first line, since the
    web UI renders this text directly rather than as Markdown.
    """
    text = summary.strip()
    if not text:
        return ""
    lines = text.splitlines()
    first = lines[0].strip()
    body = "\n".join(lines[1:]).strip()

    bare = _strip_markdown_markers(first).lower()
    if bare in ("summary:", "summary"):
        return f"{_SUMMARY_LABEL}\n\n{body}" if body else _SUMMARY_LABEL

    # A plain-text introductory heading ending with ':' that mentions "summary".
    if not first.startswith(("**", "-", "#", "* ", "+ ")):
        if bare.endswith(":") and "summary" in bare:
            return f"{_SUMMARY_LABEL}\n\n{body}" if body else _SUMMARY_LABEL

    # No recognisable heading: prepend the canonical label before the content.
    return f"{_SUMMARY_LABEL}\n\n{text}"


class ChatClient(Protocol):
    """Interface for an LLM chat backend, regardless of API dialect."""

    async def chat(
        self,
        *,
        system: str,
        user: str,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> ChatResult: ...


def build_chat_client() -> ChatClient | None:
    """Create the chat client matching the configured provider dialect.

    :returns a ``ChatClient`` instance, or ``None`` when the provider is unconfigured
    """
    provider = settings.resolved_provider()
    if provider == PROVIDER_GENERATE_CONTENT:
        return GenerateContentClient()
    if provider == PROVIDER_CHAT_COMPLETIONS:
        return ChatCompletionsClient()
    return None


class LLMService:
    """Isolates provider choice (Strategy/Adapter) from FastAPI routes."""

    def __init__(self, client: ChatClient | None = None) -> None:
        """Initialise with an explicit client or the auto-detected one."""
        self._client = client if client is not None else build_chat_client()

    async def summarize(self, report_id: str, pdf_bytes: bytes) -> str:
        """Produce a plain-language summary of a clinical PDF.

        When no LLM backend is configured, returns a deterministic stub string so
        the endpoint remains usable for smoke tests.

        :param report_id: identifier forwarded into the prompt for traceability
        :param pdf_bytes: raw PDF file contents
        :returns: summary text (never ``None``)
        """
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
            "Begin with the label 'Summary:' on its own line, then a blank line, then "
            "3-5 short bullets (one to two lines each) using bold section labels. "
            "No sentence introductions. No raw HTML. Report only what is in the text.\n"
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
        return normalize_summary_intro(text)


def extract_pdf_text(pdf_bytes: bytes) -> str:
    """Extract text from PDF bytes, truncating to ``max_pdf_chars``.

    Returns an empty string when the PDF is corrupt or text extraction fails.
    """
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
