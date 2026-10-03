"""Unit tests for the assistant service (prompt, stub, output validation)."""

from __future__ import annotations

import pytest

from app.config import settings
from app.services.assistant_service import (
    STRUCTURED_ONLY_FALLBACK,
    AssistantContext,
    AssistantInvoiceContext,
    AssistantLabReportContext,
    AssistantMedicalRecordContext,
    AssistantPrescriptionContext,
    AssistantService,
    ConversationTurn,
)
from app.services.llm_types import ChatResult


class FakeChatClient:
    """Records the prompt and returns a canned reply."""

    def __init__(self, content: str) -> None:
        """Initialise the fake with the reply content it should return."""
        self._content = content
        self.last_system: str | None = None
        self.last_user: str | None = None
        self.last_messages: list[dict] | None = None

    async def chat(
        self,
        *,
        system: str,
        user: str,
        temperature=None,
        max_tokens=None,
        messages: list[dict] | None = None,
    ) -> ChatResult:
        """Record the prompts and return the configured chat result."""
        self.last_system = system
        self.last_user = user
        self.last_messages = messages
        return ChatResult(content=self._content, prompt_tokens=None, completion_tokens=None,
                          total_tokens=None, latency_ms=0)


@pytest.mark.asyncio
async def test_chat_delegates_to_client() -> None:
    """The user message goes to the LLM; the raw reply is returned."""
    fake = FakeChatClient("Here is how to reschedule.")
    service = AssistantService(client=fake)

    reply = await service.chat("How do I reschedule an appointment?", AssistantContext())

    assert reply == "Here is how to reschedule."
    assert fake.last_user == "How do I reschedule an appointment?"


@pytest.mark.asyncio
async def test_chat_system_prompt_forbids_medical_advice() -> None:
    """The system prompt forbids diagnosis, prescriptions, and invented records."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat("Hello", AssistantContext())

    prompt = (fake.last_system or "").lower()
    assert "not a doctor" in prompt
    assert "never invent" in prompt
    assert "not medical advice" in prompt


@pytest.mark.asyncio
async def test_chat_system_prompt_includes_suggestions_guidance() -> None:
    """The system prompt must tell the model how to emit follow-up suggestions."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat("Hello", AssistantContext())

    prompt = (fake.last_system or "").lower()
    assert "suggestions" in prompt


@pytest.mark.asyncio
async def test_chat_returns_stub_when_no_provider_configured(monkeypatch: pytest.MonkeyPatch) -> None:
    """With no API key configured, a deterministic stub reply is returned."""
    monkeypatch.setattr(settings, "llm_api_key", "")
    monkeypatch.setattr(settings, "llm_provider", "")
    reply = await AssistantService().chat("What is my next appointment?", AssistantContext())
    assert "stub" in reply.lower()


def test_validate_reply_strips_whitespace() -> None:
    """Reply validation removes surrounding whitespace."""
    service = AssistantService(client=None)
    assert service.validate_reply("  Hello!  ") == "Hello!"


def test_validate_reply_rejects_empty() -> None:
    """Reply validation rejects an empty completion."""
    service = AssistantService(client=None)
    with pytest.raises(ValueError):
        service.validate_reply("   ")


def test_validate_reply_rejects_oversized() -> None:
    """Reply validation rejects content over the reply limit (6000 chars)."""
    service = AssistantService(client=None)
    with pytest.raises(ValueError):
        service.validate_reply("x" * 6001)


def test_validate_reply_rejects_diagnostic_claims() -> None:
    """Reply validation rejects diagnostic and prescriptive claims."""
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


# ---------------------------------------------------------------------------
# Suggestions-only JSON block (fix for the validation bug)
# ---------------------------------------------------------------------------

def test_validate_reply_accepts_suggestions_only_json_block() -> None:
    """A suggestions-only block is valid, and is stripped from the visible text."""
    service = AssistantService(client=None)
    reply = (
        'Sure! Here are some things you can do next.\n\n'
        '```json\n{"suggestions": ["Show my appointments", "What\'s my balance?"]}\n```'
    )
    visible = service.validate_reply(reply)
    assert "```" not in visible
    assert "suggestions" not in visible
    assert visible == "Sure! Here are some things you can do next."


def test_validate_reply_strips_actions_block() -> None:
    """The actions block must not reach the patient as raw JSON."""
    service = AssistantService(client=None)
    reply = (
        'Your invoice is paid.\n\n'
        '```json\n{"actions":[{"id":"open_invoice","label":"View invoice",'
        '"query":"open invoice 1","type":"navigate"}]}\n```'
    )
    visible = service.validate_reply(reply)
    assert visible == "Your invoice is paid."


def test_validate_reply_strips_every_block_when_actions_precede_suggestions() -> None:
    """A reply can carry two blocks; both must be removed, not just the first."""
    service = AssistantService(client=None)
    reply = (
        'Here are your results.\n\n'
        '```json\n{"actions":[{"id":"open_invoice","label":"View invoice",'
        '"query":"open invoice 1","type":"navigate"}]}\n```\n\n'
        '```json\n{"suggestions":["Show my invoices"]}\n```'
    )
    visible = service.validate_reply(reply)
    assert "```" not in visible
    assert visible == "Here are your results."


def test_validate_reply_validates_the_second_block_too() -> None:
    """The suggestions block is shape-checked even when it is not the first."""
    service = AssistantService(client=None)
    reply = (
        'Here are your results.\n\n'
        '```json\n{"actions":[{"id":"open_invoice","label":"View invoice",'
        '"query":"open invoice 1","type":"navigate"}]}\n```\n\n'
        '```json\n{"suggestions":"not-a-list"}\n```'
    )
    with pytest.raises(ValueError):
        service.validate_reply(reply)


def test_validate_reply_falls_back_when_only_a_block_was_returned() -> None:
    """A reply that was nothing but a block still leaves readable text."""
    service = AssistantService(client=None)
    reply = '```json\n{"suggestions":["Show my appointments"]}\n```'
    visible = service.validate_reply(reply)
    assert visible
    assert "```" not in visible
    assert visible == STRUCTURED_ONLY_FALLBACK


def test_validate_reply_still_rejects_disallowed_claims() -> None:
    """Moving the safety scan after stripping must not weaken it."""
    service = AssistantService(client=None)
    reply = 'You should take ibuprofen.\n\n```json\n{"suggestions":["Show reports"]}\n```'
    with pytest.raises(ValueError, match="disallowed"):
        service.validate_reply(reply)


def test_validate_actions_requires_actions_when_no_suggestions() -> None:
    """An empty JSON block with neither 'actions' nor 'suggestions' is invalid."""
    service = AssistantService(client=None)
    with pytest.raises(ValueError, match="'actions' array"):
        service._validate_actions({})


# ---------------------------------------------------------------------------
# Multi-turn conversation history
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_chat_sends_conversation_history_to_client() -> None:
    """Prior turns in conversation_history are forwarded to the LLM client."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    context = AssistantContext(
        conversation_history=[
            ConversationTurn(role="user", content="What is my next appointment?"),
            ConversationTurn(role="assistant", content="Your next appointment is on Monday."),
        ]
    )
    await service.chat("What time exactly?", context)

    assert fake.last_messages is not None
    roles = [m["role"] for m in fake.last_messages]
    assert roles == ["user", "assistant", "user"]
    assert fake.last_messages[2]["content"] == "What time exactly?"


@pytest.mark.asyncio
async def test_chat_without_history_still_sends_messages() -> None:
    """Without history, the messages list contains only the current user turn."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat("Hello", AssistantContext())

    assert fake.last_messages is not None
    assert len(fake.last_messages) == 1
    assert fake.last_messages[0]["role"] == "user"


# ---------------------------------------------------------------------------
# resolve_action — prescription and medical-record resolvers
# ---------------------------------------------------------------------------

def test_resolve_action_open_prescription() -> None:
    """open_prescription:<idx> resolves to the /prescriptions route."""
    service = AssistantService(client=None)
    context = AssistantContext(
        prescriptions=[
            AssistantPrescriptionContext(
                created_at_local="2026-01-01 09:00",
                medication_name="Atorvastatin",
                status="ACTIVE",
            )
        ]
    )
    result = service.resolve_action("open_prescription:0", context)
    assert result is not None
    assert result["route"] == "/prescriptions"
    assert result["query"]["index"] == 0


def test_resolve_action_open_prescription_out_of_range_returns_none() -> None:
    """out-of-range open_prescription index returns None (not an error)."""
    service = AssistantService(client=None)
    context = AssistantContext(
        prescriptions=[
            AssistantPrescriptionContext(
                created_at_local="2026-01-01 09:00",
                medication_name="Aspirin",
                status="ACTIVE",
            )
        ]
    )
    assert service.resolve_action("open_prescription:5", context) is None


def test_resolve_action_open_medical_record() -> None:
    """open_medical_record:<idx> resolves to the /medical-records route."""
    service = AssistantService(client=None)
    context = AssistantContext(
        medical_records=[
            AssistantMedicalRecordContext(
                created_at_local="2026-03-15 10:00",
                title="Discharge Summary",
                type="CLINICAL_DOCUMENT",
            )
        ]
    )
    result = service.resolve_action("open_medical_record:0", context)
    assert result is not None
    assert result["route"] == "/medical-records"
    assert result["query"]["index"] == 0


# ---------------------------------------------------------------------------
# Original context-rendering test (unchanged behaviour)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_chat_renders_prescription_and_billing_context() -> None:
    """The API-provided prescriptions and invoices are rendered into the prompt."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat(
        "What do I owe?",
        AssistantContext(
            prescriptions=[
                AssistantPrescriptionContext(
                    created_at_local="Tue, 22 Sep 2026 09:00",
                    medication_name="Atorvastatin",
                    dosage="10 mg nightly",
                    status="ACTIVE",
                    doctor_name="Dr. Rao",
                    refills_remaining=2,
                )
            ],
            invoices=[
                AssistantInvoiceContext(
                    created_at_local="Mon, 21 Sep 2026 08:00",
                    status="ISSUED",
                    total_cents=12000,
                    paid_cents=2000,
                    balance_cents=10000,
                    due_date_local="2026-10-05",
                )
            ],
        ),
    )

    prompt = fake.last_user or ""
    assert "SERVER-PROVIDED CONTEXT" in prompt
    assert "Atorvastatin" in prompt
    assert "refills remaining: 2" in prompt
    assert "balance 10000 cents" in prompt
    assert prompt.endswith("User question: What do I owe?")


@pytest.mark.asyncio
async def test_chat_without_context_sends_the_bare_message() -> None:
    """No snapshot means the user message is forwarded unchanged."""
    fake = FakeChatClient("ok")
    service = AssistantService(client=fake)

    await service.chat("Hello", AssistantContext())

    assert fake.last_user == "Hello"


