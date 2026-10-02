import pytest

from app.services.assistant_service import AssistantService, AssistantContext, AssistantAppointmentContext, AssistantLabReportContext


@pytest.fixture
def service():
    return AssistantService(client=None)


def test_validate_actions_basic(service):
    js = {
        "actions": [
            {"id": "open_appointment:0", "label": "View appointment", "query": "open appointment 0", "type": "navigate"}
        ],
        "suggestions": ["Show me upcoming appointments"]
    }
    # should not raise
    service._validate_actions(js)


def test_validate_actions_invalid_id(service):
    js = {"actions": [{"id": "../etc/passwd", "label": "Bad", "query": "bad"}]}
    with pytest.raises(ValueError):
        service._validate_actions(js)


def test_resolve_action_appointment(service):
    ctx = AssistantContext(appointments=[AssistantAppointmentContext(starts_at_local="2026-10-10T09:00:00", status="BOOKED")])
    out = service.resolve_action("open_appointment:0", ctx)
    assert out is not None
    assert out["route"] == "/appointments"
    assert out["query"]["index"] == 0


def test_resolve_action_out_of_range(service):
    ctx = AssistantContext(appointments=[])
    out = service.resolve_action("open_appointment:0", ctx)
    assert out is None


def test_resolve_unknown(service):
    ctx = AssistantContext()
    assert service.resolve_action("some_random_id", ctx) is None

def test_resolve_api_call_and_modal(service):
    svc = AssistantService()
    # api call mapping
    rpc = svc.resolve_action("api_call:pay_invoice:0", service)
    assert rpc is not None
    assert rpc.get("type") == "api-call"
    assert "/internal/assistant/actions/invoices/0/pay" in rpc.get("api_path")

    # modal mapping
    modal = svc.resolve_action("modal:invoice_details:0", service)
    assert modal is not None
    assert modal.get("type") == "modal"
    assert modal.get("modal") == "invoiceDetails"
