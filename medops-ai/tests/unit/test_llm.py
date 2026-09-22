"""Unit tests for LLM client retry / validation (no live provider calls)."""

from __future__ import annotations

import httpx
import pytest
import respx

from app.config import settings
from app.services.chat_completions_client import ChatCompletionsClient
from app.services.generate_content_client import GenerateContentClient
from app.services.llm_service import LLMService, normalize_summary_intro
from app.services.llm_types import LlmTimeoutError


@pytest.fixture
def enable_chat_completions(monkeypatch: pytest.MonkeyPatch) -> None:
    """Configure settings to use the chat-completions dialect."""
    monkeypatch.setattr(settings, "llm_provider", "chat_completions")
    monkeypatch.setattr(settings, "llm_api_key", "test-key")
    monkeypatch.setattr(settings, "llm_base_url", "https://llm.test/v1")
    monkeypatch.setattr(settings, "llm_model", "test-model")
    monkeypatch.setattr(settings, "llm_max_attempts", 2)
    monkeypatch.setattr(settings, "llm_retry_backoff_seconds", 0.01)
    monkeypatch.setattr(settings, "request_timeout_seconds", 2.0)


@pytest.fixture
def enable_generate_content(monkeypatch: pytest.MonkeyPatch) -> None:
    """Configure settings to use the generateContent dialect."""
    monkeypatch.setattr(settings, "llm_provider", "generate_content")
    monkeypatch.setattr(settings, "llm_api_key", "test-key")
    monkeypatch.setattr(settings, "llm_base_url", "https://genai.test/v1beta")
    monkeypatch.setattr(settings, "llm_model", "model-test")
    monkeypatch.setattr(settings, "llm_max_attempts", 2)
    monkeypatch.setattr(settings, "llm_retry_backoff_seconds", 0.01)
    monkeypatch.setattr(settings, "request_timeout_seconds", 2.0)


@pytest.mark.asyncio
@respx.mock
async def test_chat_completions_success(enable_chat_completions: None) -> None:
    """A 200 response with usage metadata is parsed correctly."""
    respx.post("https://llm.test/v1/chat/completions").mock(
        return_value=httpx.Response(
            200,
            json={
                "choices": [{"message": {"content": "  Plain overview.  "}}],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15,
                },
            },
        )
    )
    result = await ChatCompletionsClient().chat(system="sys", user="user")
    assert result.content == "  Plain overview.  "
    assert result.total_tokens == 15
    assert result.latency_ms >= 0


@pytest.mark.asyncio
@respx.mock
async def test_generate_content_success(enable_generate_content: None) -> None:
    """A generateContent 200 with candidates and usageMetadata is parsed."""
    respx.post("https://genai.test/v1beta/models/model-test:generateContent").mock(
        return_value=httpx.Response(
            200,
            json={
                "candidates": [{"content": {"parts": [{"text": "Plain overview"}]}}],
                "usageMetadata": {
                    "promptTokenCount": 8,
                    "candidatesTokenCount": 4,
                    "totalTokenCount": 12,
                },
            },
        )
    )
    result = await GenerateContentClient().chat(system="sys", user="user")
    assert result.content == "Plain overview"
    assert result.total_tokens == 12


@pytest.mark.asyncio
@respx.mock
async def test_chat_completions_retries_then_succeeds(enable_chat_completions: None) -> None:
    """A 503 followed by 200 results in a successful chat with two calls."""
    route = respx.post("https://llm.test/v1/chat/completions")
    route.side_effect = [
        httpx.Response(503, json={"error": "busy"}),
        httpx.Response(
            200,
            json={"choices": [{"message": {"content": "Recovered summary"}}]},
        ),
    ]
    result = await ChatCompletionsClient().chat(system="sys", user="user")
    assert result.content == "Recovered summary"
    assert route.call_count == 2


@pytest.mark.asyncio
@respx.mock
async def test_chat_completions_timeout_maps_error(enable_chat_completions: None) -> None:
    """A network timeout is mapped to ``LlmTimeoutError``."""
    respx.post("https://llm.test/v1/chat/completions").mock(
        side_effect=httpx.ReadTimeout("slow")
    )
    with pytest.raises(LlmTimeoutError):
        await ChatCompletionsClient().chat(system="sys", user="user")


def test_validate_summary_rejects_prescriptive_claims() -> None:
    """Summaries containing diagnostic or prescriptive language are rejected."""
    service = LLMService(client=None)
    with pytest.raises(ValueError):
        service.validate_summary("I diagnose hypertension; start taking lisinopril.")


@pytest.mark.asyncio
async def test_stub_when_no_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    """When no API key is configured, the stub summarizer returns placeholder text."""
    monkeypatch.setattr(settings, "llm_api_key", "")
    monkeypatch.setattr(settings, "llm_provider", "")
    text = await LLMService(client=None).summarize("r1", b"%PDF-1.4 x")
    assert "stub" in text.lower()


def test_normalize_summary_intro_replaces_verbose_heading() -> None:
    """A verbose leading heading is replaced with the canonical 'Summary:' label."""
    raw = (
        "Here is a summary of the diagnostic lab results:\n"
        "CBC: low hemoglobin (10.7 gm/dL)\n"
        "Glucose: 114 mg/dL, HbA1c 6.2%"
    )
    result = normalize_summary_intro(raw)
    assert result.splitlines()[0] == "Summary:"
    assert "Here is a summary" not in result
    assert "CBC: low hemoglobin (10.7 gm/dL)" in result


def test_normalize_summary_intro_keeps_existing_label() -> None:
    """An already-canonical 'Summary:' label is preserved (aside from spacing)."""
    raw = "Summary:\n**CBC:** low hemoglobin\n**Glucose:** 114 mg/dL"
    result = normalize_summary_intro(raw)
    assert result.splitlines()[0] == "Summary:"
    assert result.lstrip().startswith("Summary:\n")


def test_normalize_summary_intro_prepends_label_when_missing() -> None:
    """When the model jumps straight to content, the label is prepended."""
    raw = "**CBC:** low hemoglobin\n**Glucose:** 114 mg/dL"
    result = normalize_summary_intro(raw)
    assert result.splitlines()[0] == "Summary:"
    assert "**CBC:** low hemoglobin" in result


def test_validate_summary_normalizes_intro_label() -> None:
    """The validated summary always starts with the 'Summary:' label."""
    service = LLMService(client=None)
    raw = (
        "Here is a summary of the diagnostic lab results:\n"
        "CBC: low hemoglobin (10.7 gm/dL)\n"
        "Glucose: 114 mg/dL, HbA1c 6.2%"
    )
    result = service.validate_summary(raw)
    assert result.splitlines()[0] == "Summary:"
    assert "Here is a summary" not in result


def test_validate_summary_rejects_claim_after_intro_normalization() -> None:
    """Blocked-claim checks run on the raw text before any normalization."""
    service = LLMService(client=None)
    raw = "Here is a summary of the lab results:\nstart taking lisinopril daily."
    with pytest.raises(ValueError):
        service.validate_summary(raw)


def test_normalize_summary_intro_handles_long_star_run_without_redos() -> None:
    """A long run of '*' (untrusted LLM output) must not trigger backtracking."""
    raw = ("*" * 10_000) + "Here is a summary of the lab results:\nCBC: low hemoglobin"
    result = normalize_summary_intro(raw)
    assert result.splitlines()[0] == "Summary:"
