"""Integration tests for the assistant chat router (no live provider calls)."""

from __future__ import annotations

from collections.abc import Sequence

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.routers import assistant as assistant_router
from app.services.assistant_service import (
    AssistantContext,
    AssistantAppointmentContext,
    AssistantInvoiceContext,
    AssistantLabReportContext,
    AssistantMedicalRecordContext,
    AssistantPrescriptionContext,
)
from app.services.llm_types import LlmUnavailableError


class StubService:
    """Configurable stand-in for ``AssistantService`` at the router boundary."""

    def __init__(self, reply: str = "Stub reply") -> None:
        """Initialise the stub with the reply returned by ``chat``."""
        self._reply = reply
        self.raise_chat: Exception | None = None
        self.raise_validate: Exception | None = None
        self.last_message: str | None = None

    async def chat(
        self,
        message: str,
        context: AssistantContext,
        time_zone: str | None = None,
    ) -> str:
        """Mirror the production signature, recording the call for assertions."""
        self.last_message = message
        if self.raise_chat:
            raise self.raise_chat
        return self._reply

    def validate_reply(self, reply: str) -> str:
        """Model the production validator or raise its configured error."""
        if self.raise_validate:
            raise self.raise_validate
        text = reply.strip()
        if not text:
            # Model the real validator: empty LLM output must fail validation.
            raise ValueError("Empty assistant response")
        return text


@pytest.fixture
def client() -> TestClient:
    """Create a test client for the FastAPI application."""
    return TestClient(app)


def test_valid_request_returns_reply(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """A valid message returns the assistant reply with HTTP 200."""
    stub = StubService(reply="Open the Appointments section.")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "How do I reschedule?"})

    assert response.status_code == 200
    assert response.json() == {"message": "Open the Appointments section.", "actions": None, "suggestions": None}


def test_resolved_actions_attached(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """If the assistant returns actions, the router should attach resolved payloads."""
    # assistant reply with a JSON actions block
    reply = 'Here are options. ```json {"actions":[{"id":"open_appointment:0","label":"View appointment","query":"open appointment 0","type":"navigate"}]} ```'

    class StubWithActions(StubService):
        def __init__(self):
            super().__init__(reply=reply)
        def _extract_actions_block(self, text: str):
            import re, json

            m = re.search(r"```json\s*([\s\S]*?)\s*```", text, re.IGNORECASE)
            if not m:
                return None
            return json.loads(m.group(1))

        def resolve_action(self, aid: str, context: AssistantContext):
            # simple resolver mimicking production behaviour for tests
            if aid.startswith("open_appointment:"):
                try:
                    idx = int(aid.split(":", 1)[1])
                except Exception:
                    return None
                if len(context.appointments) > idx:
                    return {"type": "navigate", "route": "/appointments", "query": {"index": idx}}
            return None

    stub = StubWithActions()
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    # provide one appointment in the snapshot so resolution succeeds
    payload = {
        "message": "How do I reschedule?",
        "appointments": [
            {"starts_at_local": "2026-10-10T09:00:00", "status": "BOOKED"}
        ],
    }

    response = client.post("/ai/assistant/chat", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["message"].startswith("Here are options.")
    assert isinstance(body.get("actions"), list)
    assert body["actions"][0]["id"] == "open_appointment:0"
    assert body["actions"][0]["resolved"] is not None


def test_suggestions_are_returned(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """Assistant suggestions attached to JSON actions are returned to the client."""
    reply = 'Here are options. ```json {"actions":[{"id":"open_appointment:0","label":"View appointment","query":"open appointment 0","type":"navigate"}],"suggestions":["Schedule an appointment","View prescriptions"]} ```'

    class StubWithSuggestions(StubService):
        def __init__(self):
            super().__init__(reply=reply)

        def _extract_actions_block(self, text: str):
            import json, re

            m = re.search(r"```json\s*([\s\S]*?)\s*```", text, re.IGNORECASE)
            if not m:
                return None
            return json.loads(m.group(1))

    stub = StubWithSuggestions()
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "Help me with my appointments"})

    assert response.status_code == 200
    body = response.json()
    assert body["message"].startswith("Here are options.")
    assert body["actions"][0]["id"] == "open_appointment:0"
    assert body["suggestions"] == ["Schedule an appointment", "View prescriptions"]


def test_blank_message_rejected(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """A whitespace-only message is rejected with HTTP 400."""
    monkeypatch.setattr(assistant_router, "assistant_service", StubService())

    response = client.post("/ai/assistant/chat", json={"message": "   "})

    assert response.status_code == 400


def test_oversized_message_rejected(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """A message over the declared limit is rejected during validation."""
    monkeypatch.setattr(assistant_router, "assistant_service", StubService())

    response = client.post("/ai/assistant/chat", json={"message": "x" * 2001})

    assert response.status_code == 422


def test_provider_failure_maps_to_503(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """An unavailable LLM provider is exposed as HTTP 503."""
    stub = StubService()
    stub.raise_chat = LlmUnavailableError("provider down")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "Hello"})

    assert response.status_code == 503


def test_empty_llm_response_maps_to_502(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    """An empty provider response is exposed as HTTP 502."""
    stub = StubService(reply="   ")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "Hello"})

    assert response.status_code == 502
