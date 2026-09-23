/**
 * Client-side audit log for doctor dashboard actions that have no dedicated
 * server mutation: dismissing a critical alert, starting a consultation, or
 * initiating a prescription task from the command bar.
 *
 * The clinically authoritative audit trail (PRESCRIPTION_CREATED,
 * REPORT_REVIEWED, REPORT_VIEWED, APPOINTMENT_COMPLETED) is written by the API
 * service methods the dashboard routes through. This client log captures the
 * doctor's workspace-level acknowledgements so every dismiss/acknowledge is
 * attributable and reversible from the UI's audit view.
 *
 * Backed by localStorage so entries survive a refresh within the same browser;
 * all reads/writes are guarded so storage failures never throw.
 */

export type AuditAction =
  | "LAB_ACKNOWLEDGED"
  | "NOTIFICATION_ACKNOWLEDGED"
  | "CONSULTATION_STARTED"
  | "PRESCRIPTION_TASK_INITIATED";

export interface AuditEntry {
  id: string;
  action: AuditAction;
  timestamp: string;
  detail: Record<string, unknown> | null;
}

const STORAGE_KEY = "medops_doctor_audit_log";
const MAX_ENTRIES = 500;

function readRaw(): AuditEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuditEntry[]) : [];
  } catch {
    return [];
  }
}

function writeRaw(entries: AuditEntry[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch {
    /* storage unavailable — drop the entry silently */
  }
}

export function getAuditLog(): AuditEntry[] {
  return readRaw();
}

export function clearAuditLog(): void {
  writeRaw([]);
}

export function recordAudit(
  action: AuditAction,
  detail?: Record<string, unknown>,
): AuditEntry {
  const entry: AuditEntry = {
    id: crypto.randomUUID(),
    action,
    timestamp: new Date().toISOString(),
    detail: detail ?? null,
  };
  const entries = readRaw();
  entries.push(entry);
  if (entries.length > MAX_ENTRIES) {
    entries.splice(0, entries.length - MAX_ENTRIES);
  }
  writeRaw(entries);
  return entry;
}
