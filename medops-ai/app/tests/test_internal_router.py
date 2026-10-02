from app.main import app
from fastapi.testclient import TestClient


def test_metrics_endpoint():
    client = TestClient(app)
    resp = client.get("/internal/assistant/metrics")
    assert resp.status_code == 200
    data = resp.json()
    assert "assistant_chat_requests" in data


def test_pay_invoice():
    client = TestClient(app)
    resp = client.post("/internal/assistant/actions/invoices/0/pay")
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("status") == "ok"
    assert body.get("idx") == 0


def test_protected_endpoints_require_token(monkeypatch):
    # configure a token and verify endpoints reject without it
    from app.config import settings

    monkeypatch.setattr(settings, "internal_api_token", "secrettoken")
    client = TestClient(app)
    resp = client.get("/internal/assistant/metrics")
    assert resp.status_code == 401
    resp2 = client.post("/internal/assistant/actions/invoices/0/pay")
    assert resp2.status_code == 401
    # with header
    resp3 = client.get("/internal/assistant/metrics", headers={"x-internal-token": "secrettoken"})
    assert resp3.status_code == 200
