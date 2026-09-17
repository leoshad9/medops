"""Unit tests for the assistant service (prompt, stub, output validation)."""

from __future__ import annotations

import pytest

from app.config import settings
from app.services.assistant_service import AssistantService
from app.services.llm_types import ChatResult


class FakeChatClient:
    """Records the prompt and returns a canned reply."""

    def __init__(self, content: str) -> None:
        self._content = content
        self.last_system: str | None = None
        self.last_user: str | None = None

    async def chat(self, *, system: str, user: str, temperature=None, max_tokens=None) -> ChatResult:
        self.last_system = system
        self.last_user = user
        return ChatResult(content=self._content, prompt_tokens=None, completion_tokens=None,
                          total_tokens=None, latency_ms=0)


@pytest.mark.asyncio
async def test_chat_delegates_to_client() -> None:
    """The user message goes to the LLM; the raw reply is returned."""
    fake = FakeChatClient("Here is how to reschedule.")
    service = AssistantService(client=fake)

    reply = await service.chat("How do I reschedule an appointment?")

    assert reply == "Here is how to reschedule."
    assert fake.last_user == "How do I reschedule an appointment?"


@pytest.mark.asyncio
async def test_chat_system_prompt_forbids_medical_advice() -> None:
    """The system prompt forbids diagnosis, prescriptions, and invented records."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat("Hello")

    prompt = (fake.last_system or "").lower()
    assert "not a doctor" in prompt
    assert "never invent" in prompt
    assert "not medical advice" in prompt


@pytest.mark.asyncio
async def test_chat_returns_stub_when_no_provider_configured(monkeypatch: pytest.MonkeyPatch) -> None:
    """With no API key configured, a deterministic stub reply is returned."""
    monkeypatch.setattr(settings, "llm_api_key", "")
    monkeypatch.setattr(settings, "llm_provider", "")
    reply = await AssistantService().chat("What is my next appointment?")
    assert "stub" in reply.lower()


def test_validate_reply_strips_whitespace() -> None:
    service = AssistantService(client=None)
    assert service.validate_reply("  Hello!  ") == "Hello!"


def test_validate_reply_rejects_empty() -> None:
    service = AssistantService(client=None)
    with pytest.raises(ValueError):
        service.validate_reply("   ")


def test_validate_reply_rejects_oversized() -> None:
    service = AssistantService(client=None)
    with pytest.raises(ValueError):
        service.validate_reply("x" * 4001)


def test_validate_reply_rejects_diagnostic_claims() -> None:
    service = AssistantService(client=None)
    with pytest.raises(ValueError):
        service.validate_reply("You may have hypertension; I recommend treatment.")
    with pytest.raises(ValueError):
        service.validate_reply("You should take lisinopril daily.")


def test_validate_reply_allows_navigation_help_mentioning_prescriptions() -> None:
    """Navigation help must not be falsely rejected by the blocked-claims rule."""
    service = AssistantService(client=None)
    text = "You can request a refill in the Prescriptions section of MedOps."
    assert service.validate_reply(text) == text