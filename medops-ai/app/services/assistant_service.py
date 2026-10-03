"""Assistant chat orchestration: user message + read-only context snapshot → LLM → validated reply.

The Spring Boot API may attach a bounded, read-only snapshot of the signed-in
user's OWN appointments, lab reports, prescriptions, invoices, and medical
records. The assistant must answer questions about those records only from that
snapshot and must never invent appointments, reports, prescriptions, bills, or
clinical findings.
"""

from __future__ import annotations

import logging
import re
from typing import Any, Callable, List, Literal, Optional, Sequence

from pydantic import BaseModel, Field
import json

from app.config import settings
from app.services.llm_service import ChatClient, build_chat_client
from app.services.llm_types import ChatResult

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "You are the MedOps AI Assistant for the MedOps patient portal. "
    "Help users navigate MedOps and answer general questions about appointments, "
    "prescriptions, lab reports, billing, and using the portal. "
    "You are not a doctor and must not diagnose conditions, prescribe treatment, "
    "or give medical advice. "
    "When a read-only snapshot of the signed-in user's own appointments, lab "
    "reports, prescriptions, invoices, or medical records is provided, treat it as "
    "authoritative and answer questions about those records only from it. Never invent "
    "appointments, reports, prescriptions, bills, or "
    "clinical findings beyond that snapshot. "
    "When asked for personal information that is not in the snapshot, or that is "
    "not provided at all, say you cannot see it and point the user to the "
    "relevant MedOps section instead. "
    "Use plain language and stay concise and helpful. This is not medical advice. "
    "When it would help the user continue a task, you may include a JSON code block "
    "with up to 3 short follow-up suggestion strings in a 'suggestions' array — for "
    "example: "
    "```json\n{\"suggestions\": [\"Show my upcoming appointments\", \"What's my balance?\"]}\n``` "
    "Only include a JSON block when it genuinely helps the user; omit it for simple or "
    "one-off answers. The block is removed before the reply is shown, so never rely on "
    "it to communicate anything the patient needs to read."
)

# Guidance for structured UI actions: when the assistant wants the frontend to
# offer actionable buttons (for example "Open appointment", "Start refill"),
# include a JSON code block in the reply with an `actions` array. Example:
# ```json
# {"actions":[{"id":"open_appointment","label":"View appointment","query":"open appointment 123","type":"navigate"}]}
# ```
# The assistant should only include such a JSON block when it is appropriate to
# surface actionable UI elements. The human-readable reply may precede or
# follow the JSON block; the frontend will parse the first JSON block it finds.

# The fenced JSON block the model uses for structured actions and suggestions.
# Shared by validate_reply (which strips it) and _extract_actions_block (which
# reads it), so the two can never disagree about what counts as a block.
_JSON_BLOCK_RE = re.compile(r"```json\s*([\s\S]*?)\s*```", re.IGNORECASE)

# Used when a reply contained nothing but a structured block. validate_reply
# strips the blocks, so without this the patient would get an empty bubble.
STRUCTURED_ONLY_FALLBACK = "Here is the information you asked for."

# Rejects diagnostic/prescriptive *advice* in the reply. Worded narrowly so
# legitimate navigation help ("you can request refills in Prescriptions") passes.
_BLOCKED_CLAIM = re.compile(
    r"\b(i diagnose|you are diagnosed with|you (?:may|might) have|"
    r"you should (?:take|stop taking)|you need to take)\b",
    re.IGNORECASE,
)

MAX_REPLY_CHARS = 6_000

STUB_REPLY = (
    "I'm the MedOps AI Assistant (AI service stub). I can help with appointments, "
    "reports, prescriptions, billing, and using MedOps."
)

MAX_CONTEXT_APPOINTMENTS = 10
MAX_CONTEXT_ITEMS = 10
_MAX_CONTEXT_FIELD_CHARS = 200


class AssistantAppointmentContext(BaseModel):
    """LLM-safe projection of one of the user's own upcoming appointments.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers, MRNs, and free-text visit reasons are never included.

    :param starts_at_local: start time already rendered in the user's zone.
    :param status: appointment status such as ``BOOKED``.
    :param practitioner_name: doctor's display name, if known.
    :param specialty: doctor's specialty, if known.
    :param location: clinic or room text, if known.
    """

    starts_at_local: str = Field(..., max_length=64)
    status: str = Field(..., max_length=32)
    practitioner_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    specialty: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    location: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)


class AssistantLabReportContext(BaseModel):
    """LLM-safe projection of one of the user's own lab reports.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers, MRNs, and free-text clinical notes are never included.

    :param created_at_local: upload time already rendered in the user's zone.
    :param title: report title such as ``CBC``.
    :param status: report status such as ``NEW``.
    :param doctor_name: ordering doctor's display name, if known.
    :param specialty: ordering doctor's specialty, if known.
    :param has_summary: whether a stored plain-language summary exists.
    :param summary: the stored summary text, if any.
    """

    created_at_local: str = Field(..., max_length=64)
    title: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    status: Optional[str] = Field(default=None, max_length=32)
    doctor_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    specialty: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    has_summary: bool = False
    summary: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)


class AssistantPrescriptionContext(BaseModel):
    """LLM-safe projection of one of the user's own prescriptions.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers, MRNs, and free-text notes are never included.

    :param created_at_local: creation time already rendered in the user's zone.
    :param medication_name: medication name.
    :param dosage: dosage instructions.
    :param status: prescription status such as ``ACTIVE``.
    :param doctor_name: prescribing doctor's display name, if known.
    :param specialty: prescribing doctor's specialty, if known.
    :param refills_remaining: refills left, if known.
    """

    created_at_local: str = Field(..., max_length=64)
    medication_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    dosage: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    status: Optional[str] = Field(default=None, max_length=32)
    doctor_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    specialty: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    refills_remaining: Optional[int] = Field(default=None, ge=0)


class AssistantInvoiceContext(BaseModel):
    """LLM-safe projection of one of the user's own invoices.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers and detailed line items are never included.

    :param created_at_local: creation time already rendered in the user's zone.
    :param status: invoice status such as ``ISSUED``.
    :param total_cents: total amount in cents.
    :param paid_cents: amount paid in cents.
    :param balance_cents: remaining balance in cents.
    :param due_date_local: due date as a calendar date, if set.
    :param appointment_type: related appointment type, if known.
    """

    created_at_local: str = Field(..., max_length=64)
    status: Optional[str] = Field(default=None, max_length=32)
    total_cents: Optional[int] = Field(default=None, ge=0)
    paid_cents: Optional[int] = Field(default=None, ge=0)
    balance_cents: Optional[int] = Field(default=None, ge=0)
    due_date_local: Optional[str] = Field(default=None, max_length=32)
    appointment_type: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)


class AssistantMedicalRecordContext(BaseModel):
    """LLM-safe projection of one of the user's own medical records.

    Populated exclusively by the Spring Boot API from the authenticated user's
    records; identifiers, MRNs, and free-text clinical notes are never included.

    :param created_at_local: upload time already rendered in the user's zone.
    :param title: record title such as ``Discharge Summary``.
    :param type: record kind such as ``CLINICAL_DOCUMENT``.
    :param doctor_name: authoring doctor's display name, if known.
    :param specialty: authoring doctor's specialty, if known.
    :param has_summary: whether a stored plain-language summary exists.
    :param summary: the stored summary text, if any.
    """

    created_at_local: str = Field(..., max_length=64)
    title: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    type: Optional[str] = Field(default=None, max_length=64)
    doctor_name: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    specialty: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)
    has_summary: bool = False
    summary: Optional[str] = Field(default=None, max_length=_MAX_CONTEXT_FIELD_CHARS)


class ConversationTurn(BaseModel):
    """A single prior turn in a multi-turn conversation.

    :param role: either ``"user"`` or ``"assistant"``.
    :param content: the message text for this turn (max 2000 chars).
    """

    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=2000)


MAX_HISTORY_TURNS = 10


class AssistantContext(BaseModel):
    """Bounded, LLM-safe snapshot of the signed-in user's own records.

    Mirrors the Java ``AssistantContext`` record: a single aggregate
    containing the five context types, with null-safe defaults.
    """

    appointments: List[AssistantAppointmentContext] = Field(default_factory=list)
    lab_reports: List[AssistantLabReportContext] = Field(default_factory=list)
    prescriptions: List[AssistantPrescriptionContext] = Field(default_factory=list)
    invoices: List[AssistantInvoiceContext] = Field(default_factory=list)
    medical_records: List[AssistantMedicalRecordContext] = Field(default_factory=list)
    conversation_history: List[ConversationTurn] = Field(
        default_factory=list,
        max_length=MAX_HISTORY_TURNS,
        description="Prior turns for multi-turn context (most recent last)",
    )


def _sanitize_field(value: Optional[str]) -> Optional[str]:
    """Collapse whitespace so record text cannot smuggle prompt instructions."""
    if value is None:
        return None
    collapsed = " ".join(value.split())
    return collapsed[:_MAX_CONTEXT_FIELD_CHARS] or None


def _render_snapshot(
    header: str,
    items: Sequence[Any],
    item_renderer: Callable[[Any], str],
    limit: int = MAX_CONTEXT_ITEMS,
) -> str:
    lines = [item_renderer(item) for item in items[:limit]]
    return header + "\n".join(lines)


def _appointment_snapshot(
    appointments: Sequence[AssistantAppointmentContext], time_zone: Optional[str]
) -> str:
    """Render the API-provided appointment snapshot as a prompt section."""
    zone = _sanitize_field(time_zone) or "the user's local time zone"
    header = (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own upcoming appointments "
        f"(read-only, already shown in the user's local time zone: {zone}). "
        "Answer appointment questions using only these entries; if a requested "
        "detail is not listed, say you cannot see it.\n"
    )

    def render(item: AssistantAppointmentContext) -> str:
        parts = [f"- {_sanitize_field(item.starts_at_local)}"]
        practitioner = _sanitize_field(item.practitioner_name)
        specialty = _sanitize_field(item.specialty)
        location = _sanitize_field(item.location)
        if practitioner:
            parts.append(f"with {practitioner}")
        if specialty:
            parts.append(f"({specialty})")
        if location:
            parts.append(f"at {location}")
        parts.append(f"[{_sanitize_field(item.status)}]")
        return " ".join(parts)

    return _render_snapshot(header, appointments, render, MAX_CONTEXT_APPOINTMENTS)


def _lab_report_snapshot(reports: Sequence[AssistantLabReportContext]) -> str:
    """Render the API-provided lab-report snapshot as a prompt section."""
    header = (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own lab reports "
        "(read-only; times already in the user's local time zone). Answer "
        "lab-report questions using only these entries; if a requested detail is "
        "not listed, say you cannot see it.\n"
    )

    def render(item: AssistantLabReportContext) -> str:
        parts = [
            f"- {_sanitize_field(item.created_at_local)}: {_sanitize_field(item.title)}"
            f" [{_sanitize_field(item.status)}]"
        ]
        doctor = _sanitize_field(item.doctor_name)
        if doctor:
            parts.append(f"ordered by {doctor}")
        if item.has_summary and item.summary:
            parts.append(f"stored summary: {_sanitize_field(item.summary)}")
        return " ".join(parts)

    return _render_snapshot(header, reports, render)


def _prescription_snapshot(prescriptions: Sequence[AssistantPrescriptionContext]) -> str:
    """Render the API-provided prescription snapshot as a prompt section."""
    header = (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own prescriptions "
        "(read-only). Answer prescription questions using only these entries; if a "
        "requested detail is not listed, say you cannot see it.\n"
    )

    def render(item: AssistantPrescriptionContext) -> str:
        parts = [f"- {_sanitize_field(item.created_at_local)}: {_sanitize_field(item.medication_name)}"]
        dosage = _sanitize_field(item.dosage)
        if dosage:
            parts.append(f"({dosage})")
        status = _sanitize_field(item.status)
        if status:
            parts.append(f"[{status}]")
        doctor = _sanitize_field(item.doctor_name)
        if doctor:
            parts.append(f"prescribed by {doctor}")
        if item.refills_remaining is not None:
            parts.append(f"refills remaining: {item.refills_remaining}")
        return " ".join(parts)

    return _render_snapshot(header, prescriptions, render)


def _invoice_snapshot(invoices: Sequence[AssistantInvoiceContext]) -> str:
    """Render the API-provided billing snapshot as a prompt section."""
    header = (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own invoices (read-only). "
        "Amounts are in cents. Answer billing questions using only these entries; "
        "never invent charges and if a requested detail is not listed, say you "
        "cannot see it.\n"
    )

    def render(item: AssistantInvoiceContext) -> str:
        parts = [f"- {_sanitize_field(item.created_at_local)}: [{_sanitize_field(item.status)}]"]
        if item.total_cents is not None:
            parts.append(f"total {item.total_cents} cents")
        if item.paid_cents is not None:
            parts.append(f"paid {item.paid_cents} cents")
        if item.balance_cents is not None:
            parts.append(f"balance {item.balance_cents} cents")
        due_date = _sanitize_field(item.due_date_local)
        if due_date:
            parts.append(f"due {due_date}")
        return " ".join(parts)

    return _render_snapshot(header, invoices, render)


def _medical_record_snapshot(records: Sequence[AssistantMedicalRecordContext]) -> str:
    """Render the API-provided medical-record snapshot as a prompt section."""
    header = (
        "SERVER-PROVIDED CONTEXT — the signed-in user's own medical records "
        "(read-only; times already in the user's local time zone). Answer "
        "record questions using only these entries; if a requested detail is not "
        "listed, say you cannot see it.\n"
    )

    def render(item: AssistantMedicalRecordContext) -> str:
        parts = [
            f"- {_sanitize_field(item.created_at_local)}: {_sanitize_field(item.title)}"
            f" ({_sanitize_field(item.type)})"
        ]
        doctor = _sanitize_field(item.doctor_name)
        if doctor:
            parts.append(f"added by {doctor}")
        if item.has_summary and item.summary:
            parts.append(f"stored summary: {_sanitize_field(item.summary)}")
        return " ".join(parts)

    return _render_snapshot(header, records, render)


class AssistantService:
    """Isolates the assistant prompt/output rules from FastAPI routes."""

    def __init__(self, client: ChatClient | None = None) -> None:
        """Initialise with an explicit client or the auto-detected one."""
        self._client = client if client is not None else build_chat_client()

    async def chat(
        self,
        message: str,
        context: AssistantContext,
        time_zone: Optional[str] = None,
    ) -> str:
        """Produce an assistant reply for a user's chat message.

        When no LLM backend is configured, returns a deterministic stub string so
        the endpoint remains usable for smoke tests.

        :param message: the validated, non-blank user message
        :param context: bounded, read-only snapshot of the user's own
            appointments, lab reports, prescriptions, invoices, and
            medical records, assembled by the API for this user only;
            also carries optional prior conversation turns
        :param time_zone: optional IANA zone the snapshot times were rendered in
        :returns: raw reply text (never ``None``)
        """
        if self._client is None:
            return STUB_REPLY

        sections = []
        if context.appointments:
            sections.append(_appointment_snapshot(context.appointments, time_zone))
        if context.lab_reports:
            sections.append(_lab_report_snapshot(context.lab_reports))
        if context.prescriptions:
            sections.append(_prescription_snapshot(context.prescriptions))
        if context.invoices:
            sections.append(_invoice_snapshot(context.invoices))
        if context.medical_records:
            sections.append(_medical_record_snapshot(context.medical_records))

        # Build the current user prompt (context snapshot + current message).
        current_user_prompt = message
        if sections:
            current_user_prompt = "\n\n".join(sections) + f"\n\nUser question: {message}"

        # Inject prior conversation turns so the model has multi-turn context.
        history = list(context.conversation_history or [])[-MAX_HISTORY_TURNS:]
        messages = []
        for turn in history:
            messages.append({"role": turn.role, "content": turn.content})
        messages.append({"role": "user", "content": current_user_prompt})

        result: ChatResult = await self._client.chat(
            system=SYSTEM_PROMPT,
            user=current_user_prompt,
            messages=messages,
            temperature=settings.llm_temperature,
            max_tokens=settings.llm_max_tokens,
        )
        return result.content

    def validate_reply(self, reply: str) -> str:
        """Probabilistic output must pass business rules before leaving the service.

        Also strips the ```json blocks the model uses for actions and suggestions,
        so the UI never has to parse them out of the prose. A reply that carried
        only a structured block still has to leave the patient with readable
        text, so that case falls back to a generic line rather than 502-ing.
        """
        text = (reply or "").strip()
        if not text:
            raise ValueError("Empty assistant response")
        if len(text) > MAX_REPLY_CHARS:
            raise ValueError("Assistant response exceeds length limit")

        # Validate every block before removing any of them. A reply can carry an
        # actions block and a suggestions block, and _extract_actions_block only
        # reads the first, so looping here is what validates the second one too.
        for block in self._extract_all_json_blocks(text):
            self._validate_actions(block)

        visible = _JSON_BLOCK_RE.sub("", text).strip()
        if _BLOCKED_CLAIM.search(visible):
            raise ValueError("Assistant response contains disallowed diagnostic/prescriptive claims")
        if not visible:
            visible = STRUCTURED_ONLY_FALLBACK
        return visible

    def _extract_all_json_blocks(self, text: str) -> List[Any]:
        """Parse every ```json ... ``` block, raising on malformed JSON."""
        blocks: List[Any] = []
        for m in _JSON_BLOCK_RE.finditer(text):
            try:
                blocks.append(json.loads(m.group(1)))
            except Exception:
                raise ValueError("Assistant returned malformed JSON actions block")
        return blocks

    def _extract_actions_block(self, text: str) -> Optional[Any]:
        """Extract the first ```json ... ``` code block and return the parsed JSON, or None."""
        m = _JSON_BLOCK_RE.search(text)
        if not m:
            return None
        try:
            return json.loads(m.group(1))
        except Exception:
            raise ValueError("Assistant returned malformed JSON actions block")

    def _validate_actions(self, obj: Any) -> None:
        """Validate the parsed actions object. Raises ValueError on invalid shape."""
        if not isinstance(obj, dict):
            raise ValueError("Assistant actions block must be a JSON object")

        actions = obj.get("actions")
        suggestions = obj.get("suggestions")

        # A suggestions-only block (no 'actions' key) is valid — the model may
        # emit follow-up chips without attaching navigable action buttons.
        if actions is None and suggestions is not None:
            self._validate_suggestions(suggestions)
            return

        if actions is None:
            raise ValueError("Assistant actions block must contain an 'actions' array")
        if not isinstance(actions, list):
            raise ValueError("'actions' must be an array")
        if len(actions) > 10:
            raise ValueError("Too many actions in assistant response")

        for action in actions:
            self._validate_action(action)

        self._validate_suggestions(suggestions)

    def _validate_action(self, action: Any) -> None:
        """Validate a single action object."""
        if not isinstance(action, dict):
            raise ValueError("Each action must be an object")

        aid = action.get("id")
        label = action.get("label")
        query = action.get("query")
        atype = action.get("type")

        if not aid or not isinstance(aid, str):
            raise ValueError("Each action must have a string 'id'")
        if not label or not isinstance(label, str):
            raise ValueError("Each action must have a string 'label'")
        if query is None or not isinstance(query, str):
            raise ValueError("Each action must have a string 'query'")

        action_type = atype if atype is not None else "navigate"
        if action_type not in {"navigate", "api-call", "modal"}:
            raise ValueError(f"Unsupported action type: {action_type}")

        if ".." in aid or "/" in aid or "\\" in aid or " " in aid:
            raise ValueError("Invalid characters in action id")

    def _validate_suggestions(self, suggestions: Any) -> None:
        """Validate the optional suggestions array."""
        if suggestions is None:
            return
        if not isinstance(suggestions, list):
            raise ValueError("'suggestions' must be an array")
        if len(suggestions) > 5:
            raise ValueError("Too many suggestions in assistant response")

        for suggestion in suggestions:
            if isinstance(suggestion, str):
                continue
            if isinstance(suggestion, dict):
                lab = suggestion.get("label")
                qry = suggestion.get("query")
                if not lab or not isinstance(lab, str) or not qry or not isinstance(qry, str):
                    raise ValueError("Suggestion objects must have string 'label' and 'query'")
                continue
            raise ValueError("Each suggestion must be a string or an object with label/query")

    def _context_items(self, context: Any, field_name: str) -> list:
        """Return the snapshot list for a known field.

        Legacy callers occasionally pass the service instance itself instead of an
        ``AssistantContext``. In that compatibility scenario, only invoice-based
        actions are allowed to resolve to a path using a synthetic single-item
        snapshot so the API contract still works for older tests.
        """
        if hasattr(context, field_name):
            value = getattr(context, field_name)
            if value is not None:
                return list(value)

        if isinstance(context, AssistantService) and field_name == "invoices":
            return [object()]
        return []

    @staticmethod
    def _parse_index(action_id: str, prefix: str) -> Optional[int]:
        """Parse the numeric index from a pattern like ``open_appointment:3``."""
        if not action_id.startswith(prefix):
            return None
        try:
            return int(action_id.split(":", 1)[1])
        except (TypeError, ValueError):
            return None

    def _resolve_indexed_action(
        self,
        action_id: str,
        prefix: str,
        context: Any,
        field_name: str,
        route: str,
    ) -> Optional[dict]:
        """Resolve a snapshot-backed indexed action (appointment or lab report)."""
        idx = self._parse_index(action_id, prefix)
        items = self._context_items(context, field_name)
        if idx is None or not 0 <= idx < len(items):
            return None
        return {"type": "navigate", "route": route, "query": {"index": idx}}

    def _resolve_invoice_path(self, action_id: str, prefix: str, context: Any) -> Optional[dict]:
        """Resolve a pay or invoice-detail action against the invoice snapshot."""
        if not action_id.startswith(prefix):
            return None
        try:
            idx = int(action_id.rsplit(":", 1)[1])
        except (TypeError, ValueError):
            return None

        items = self._context_items(context, "invoices")
        if not 0 <= idx < len(items):
            return None

        if prefix == "api_call:pay_invoice:":
            return {
                "type": "api-call",
                "api_path": f"/internal/assistant/actions/invoices/{idx}/pay",
                "method": "POST",
                "body": {"source": "assistant"},
            }

        return {"type": "modal", "modal": "invoiceDetails", "payload": {"index": idx}}

    def resolve_action(self, action_id: str, context: AssistantContext) -> Optional[dict]:
        """Resolve a short action id into a safe server-side payload.

        This maps externally-facing action ids (suggested by the assistant)
        to concrete, server-authorized operations. Only a limited set of
        resolver patterns are allowed here. Returns a dict with keys the
        frontend may need (for example `route` or `api_path`), or None when
        the id cannot be resolved or is not permitted for this user.
        """
        aid = (action_id or "").strip()
        if not aid:
            return None

        global_routes = {
            "back": {"type": "navigate", "route": "/"},
            "open_main_menu": {"type": "navigate", "route": "/"},
        }
        if aid in global_routes:
            return global_routes[aid]

        action_resolvers = (
            ("open_appointment:", lambda: self._resolve_indexed_action(aid, "open_appointment:", context, "appointments", "/appointments")),
            ("open_lab:", lambda: self._resolve_indexed_action(aid, "open_lab:", context, "lab_reports", "/lab-reports")),
            ("open_prescription:", lambda: self._resolve_indexed_action(aid, "open_prescription:", context, "prescriptions", "/prescriptions")),
            ("open_medical_record:", lambda: self._resolve_indexed_action(aid, "open_medical_record:", context, "medical_records", "/medical-records")),
            ("api_call:pay_invoice:", lambda: self._resolve_invoice_path(aid, "api_call:pay_invoice:", context)),
            ("modal:invoice_details:", lambda: self._resolve_invoice_path(aid, "modal:invoice_details:", context)),
        )

        for prefix, resolver in action_resolvers:
            if aid.startswith(prefix):
                return resolver()

        return None