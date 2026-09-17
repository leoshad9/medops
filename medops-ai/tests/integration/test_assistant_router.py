"""Integration tests for the assistant chat router (no live provider calls)."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.routers import assistant as assistant_router
from app.services.llm_types import LlmUnavailableError


class StubService:
    """Configurable stand-in for ``AssistantService`` at the router boundary."""

    def __init__(self, reply: str = "Stub reply") -> None:
        self._reply = reply
        self.raise_chat: Exception | None = None
        self.raise_validate: Exception | None = None

    async def chat(self, message: str) -> str:
        if self.raise_chat:
            raise self.raise_chat
        return self._reply

    def validate_reply(self, reply: str) -> str:
        if self.raise_validate:
            raise self.raise_validate
        text = reply.strip()
        if not text:
            # Model the real validator: empty LLM output must fail validation.
            raise ValueError("Empty assistant response")
        return text


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_valid_request_returns_reply(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubService(reply="Open the Appointments section.")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "How do I reschedule?"})

    assert response.status_code == 200
    assert response.json() == {"message": "Open the Appointments section."}


def test_blank_message_rejected(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(assistant_router, "assistant_service", StubService())

    response = client.post("/ai/assistant/chat", json={"message": "   "})

    assert response.status_code == 400


def test_oversized_message_rejected(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(assistant_router, "assistant_service", StubService())

    response = client.post("/ai/assistant/chat", json={"message": "x" * 2001})

    assert response.status_code == 422


def test_provider_failure_maps_to_503(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubService()
    stub.raise_chat = LlmUnavailableError("provider down")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "Hello"})

    assert response.status_code == 503


def test_empty_llm_response_maps_to_502(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    stub = StubService(reply="   ")
    monkeypatch.setattr(assistant_router, "assistant_service", stub)

    response = client.post("/ai/assistant/chat", json={"message": "Hello"})

    assert response.status_code == 502