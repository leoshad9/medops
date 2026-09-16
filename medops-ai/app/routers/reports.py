"""Clinical report summarization endpoints."""

from __future__ import annotations

import base64
import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.llm_service import LLMService
from app.services.llm_types import (
    LlmError,
    LlmRateLimitError,
    LlmTimeoutError,
    LlmUnavailableError,
)

router = APIRouter(prefix="/ai", tags=["reports"])

logger = logging.getLogger(__name__)

llm_service = LLMService()


class SummarizeRequest(BaseModel):
    """Request payload for clinical report summarization.

    :param content_base64: Base64-encoded PDF bytes (minimum 8 characters).
    :param content_type: MIME type of the content; only ``application/pdf`` is accepted.
    """

    content_base64: str = Field(..., min_length=8, description="PDF bytes as base64")
    content_type: str = Field(default="application/pdf")


class SummarizeResponse(BaseModel):
    """Response payload containing the generated report summary.

    :param report_id: Identifier of the summarized report.
    :param summary: The AI-generated summary text.
    """

    report_id: str
    summary: str


@router.post("/reports/{report_id}/summary", response_model=SummarizeResponse)
async def summarize_report(report_id: str, body: SummarizeRequest):
    """Summarize a clinical report PDF using the configured LLM service.

    Accepts a base64-encoded PDF, validates it, and requests a summary from
    the LLM backend.  Provider-specific errors (timeouts, rate limits,
    unavailable errors) are mapped to appropriate HTTP status codes;
    provider-specific exception details are logged server-side only and
    never exposed to the client.

    :param report_id: Identifier of the report to summarize.
    :param body: Request containing the base64 PDF content.
    :returns: The generated summary text keyed by report ID.
    """
    if body.content_type.lower() != "application/pdf":
        raise HTTPException(status_code=400, detail="Only application/pdf is accepted")

    try:
        pdf_bytes = base64.b64decode(body.content_base64, validate=True)
    except Exception as exc:  # noqa: BLE001 — map any decode failure to 400
        raise HTTPException(status_code=400, detail="Invalid content_base64") from exc

    if not pdf_bytes.startswith(b"%PDF"):
        raise HTTPException(status_code=400, detail="Payload is not a PDF")

    # Do not log PDF bytes or completions.
    logger.info("summarize_report accepted report_id=%s bytes=%s", report_id, len(pdf_bytes))

    try:
        raw = await llm_service.summarize(report_id, pdf_bytes)
        summary = llm_service.validate_summary(raw)
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="Summarizer returned an unusable result") from exc
    except LlmTimeoutError as exc:
        raise HTTPException(status_code=504, detail="LLM request timed out") from exc
    except LlmRateLimitError as exc:
        logger.warning("LLM rate limit for report_id=%s: %s", report_id, exc)
        raise HTTPException(
            status_code=429,
            detail="LLM provider rate limit exceeded. Please try again shortly.",
        ) from exc
    except LlmUnavailableError as exc:
        raise HTTPException(status_code=503, detail="LLM provider unavailable") from exc
    except LlmError as exc:
        raise HTTPException(status_code=502, detail="LLM summarization failed") from exc

    return SummarizeResponse(report_id=report_id, summary=summary)
