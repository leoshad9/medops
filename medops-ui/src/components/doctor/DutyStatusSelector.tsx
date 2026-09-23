import { useEffect, useRef, useState } from "react";
import { CheckCircle, Clock, MapPin, Moon } from "lucide-react";

import { DUTY_STATUS_OPTIONS } from "../../services/doctorService";
import type { DoctorDutyStatus, DutyStatusOption } from "../../types/doctor";

interface DutyStatusSelectorProps {
  status: DoctorDutyStatus;
  onChange: (status: DoctorDutyStatus) => Promise<void>;
  needsConfirmation: (status: DoctorDutyStatus) => boolean;
  isChanging: boolean;
  error: string | null;
  pendingItems: string[];
}

const STATUS_ICONS: Record<DoctorDutyStatus, typeof CheckCircle> = {
  ON_DUTY: CheckCircle,
  BUSY: Clock,
  AWAY: MapPin,
  OFF_DUTY: Moon,
};

export function DutyStatusSelector({
  status,
  onChange,
  needsConfirmation,
  isChanging,
  error,
  pendingItems,
}: Readonly<DutyStatusSelectorProps>) {
  const [isOpen, setIsOpen] = useState(false);
  const [confirmingOffDuty, setConfirmingOffDuty] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        menuRef.current &&
        !menuRef.current.contains(event.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
        setConfirmingOffDuty(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
        setConfirmingOffDuty(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const handleSelect = async (newStatus: DoctorDutyStatus) => {
    if (newStatus === status) {
      setIsOpen(false);
      return;
    }
    if (newStatus === "OFF_DUTY" && needsConfirmation(newStatus)) {
      setConfirmingOffDuty(true);
      return;
    }
    setIsOpen(false);
    await onChange(newStatus);
  };

  const handleConfirm = async () => {
    setIsOpen(false);
    setConfirmingOffDuty(false);
    await onChange("OFF_DUTY");
  };

  const handleCancel = () => {
    setConfirmingOffDuty(false);
  };

  const option = DUTY_STATUS_OPTIONS[status];
  const Icon = STATUS_ICONS[status];

  return (
    <div className="relative inline-block">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-label={`Duty status: ${option.label}. Click to change.`}
        className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${option.badge} focus-visible-ring transition`}
        data-testid="duty-status-button"
      >
        {isChanging ? (
          <span className="animate-pulse">…</span>
        ) : (
          <>
            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
            {option.label}
          </>
        )}
      </button>

      {isOpen && (
        <div
          ref={menuRef}
          className="absolute top-full left-0 z-30 mt-1 w-64 rounded-xl border border-brand-line bg-white shadow-lg"
          role="menu"
          aria-label="Change duty status"
          data-testid="duty-status-menu"
        >
          {confirmingOffDuty ? (
            <div className="p-4">
              <h3 className="text-sm font-bold text-brand-ink">
                Confirm Off Duty
              </h3>
              <p className="mt-1 text-xs text-brand-muted">
                You have pending clinical work that requires attention:
              </p>
              <ul className="mt-2 space-y-1 text-xs text-brand-muted">
                {pendingItems.map((item, idx) => (
                  <li key={idx} className="flex items-center gap-1.5">
                    <span className="h-1 w-1 rounded-full bg-brand-rust" aria-hidden="true" />
                    {item}
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-xs text-brand-rust">
                Switching to Off Duty may delay patient care.
              </p>
              <div className="mt-4 flex gap-2">
                <button
                  type="button"
                  onClick={() => void handleCancel()}
                  className="flex-1 rounded-lg border border-brand-line bg-white px-3 py-2 text-xs font-semibold text-brand-ink hover:bg-slate-50 focus-visible-ring"
                  data-testid="cancel-off-duty"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => void handleConfirm()}
                  className="flex-1 rounded-lg bg-brand-rust px-3 py-2 text-xs font-bold text-white hover:bg-brand-rust/90 focus-visible-ring"
                  data-testid="confirm-off-duty"
                >
                  Go Off Duty
                </button>
              </div>
            </div>
          ) : (
            <div className="py-1">
              {(Object.values(DUTY_STATUS_OPTIONS) as DutyStatusOption[]).map((opt) => {
                const OptIcon = STATUS_ICONS[opt.value];
                const isSelected = opt.value === status;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    role="menuitemradio"
                    aria-checked={isSelected}
                    onClick={() => void handleSelect(opt.value)}
                    disabled={isChanging}
                    className={`flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm focus-visible-ring disabled:opacity-60 ${
                      isSelected
                        ? "bg-brand-primary-tint font-semibold text-brand-primary-dark"
                        : "text-brand-ink hover:bg-slate-50"
                    }}`}
                    data-testid={`duty-option-${opt.value}`}
                  >
                    <OptIcon className="h-4 w-4" aria-hidden="true" />
                    <span className="flex-1">{opt.label}</span>
                    {isSelected && <CheckCircle className="h-3.5 w-3.5 text-brand-primary" />}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {error && (
        <p
          className="mt-1 text-xs text-brand-rust"
          role="alert"
          aria-live="polite"
        >
          {error}
        </p>
      )}
    </div>
  );
}
