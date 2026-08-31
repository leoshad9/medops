"""Unit tests for LLM client retry / validation (no live provider calls)."""

from __future__ import annotations

import httpx
import pytest
import respx

from app.config import settings
from app.services.chat_completions_client import ChatCompletionsClient
from app.services.generate_content_client import GenerateContentClient
from app.services.llm_service import LLMService
from app.services.llm_types import LlmTimeoutError


@pytest.fixture
def enable_chat_completions(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "llm_provider", "chat_completions")
    monkeypatch.setattr(settings, "llm_api_key", "test-key")
    monkeypatch.setattr(settings, "llm_base_url", "https://llm.test/v1")
    monkeypatch.setattr(settings, "llm_model", "test-model")
    monkeypatch.setattr(settings, "llm_max_attempts", 2)
    monkeypatch.setattr(settings, "llm_retry_backoff_seconds", 0.01)
    monkeypatch.setattr(settings, "request_timeout_seconds", 2.0)


@pytest.fixture
def enable_generate_content(monkeypatch: pytest.MonkeyPatch) -> None:
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
    respx.post("https://llm.test/v1/chat/completions").mock(
        side_effect=httpx.ReadTimeout("slow")
    )
    with pytest.raises(LlmTimeoutError):
        await ChatCompletionsClient().chat(system="sys", user="user")


def test_validate_summary_rejects_prescriptive_claims() -> None:
    service = LLMService(client=None)
    with pytest.raises(ValueError):
        service.validate_summary("I diagnose hypertension; start taking lisinopril.")


@pytest.mark.asyncio
async def test_stub_when_no_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "llm_api_key", "")
    monkeypatch.setattr(settings, "llm_provider", "")
    text = await LLMService(client=None).summarize("r1", b"%PDF-1.4 x")
    assert "stub" in text.lower()
