import { useCallback, useState } from "react";

import {
  appendDutyStatusLog,
  getStoredDutyStatus,
  saveDutyStatus,
} from "../services/doctorService";
import type { DoctorDutyStatus } from "../types/doctor";

export interface UseDoctorDutyStatusResult {
  status: DoctorDutyStatus;
  isChanging: boolean;
  error: string | null;
  changeStatus: (status: DoctorDutyStatus) => Promise<void>;
  needsConfirmation: (status: DoctorDutyStatus) => boolean;
  clearError: () => void;
}

export function useDoctorDutyStatus(
  doctorEmail: string,
  hasPendingClinicalWork: boolean = false,
): UseDoctorDutyStatusResult {
  const [status, setStatus] = useState<DoctorDutyStatus>(() => getStoredDutyStatus());
  const [isChanging, setIsChanging] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const changeStatus = useCallback(
    async (newStatus: DoctorDutyStatus) => {
      if (newStatus === status) {
        return;
      }
      const previousStatus = status;
      setIsChanging(true);
      setError(null);
      try {
        saveDutyStatus(newStatus);
        appendDutyStatusLog({
          doctorId: doctorEmail,
          previousStatus,
          newStatus,
          timestamp: new Date().toISOString(),
        });
        setStatus(newStatus);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Unable to update duty status.");
      } finally {
        setIsChanging(false);
      }
    },
    [status, doctorEmail],
  );

  const needsConfirmation = useCallback(
    (newStatus: DoctorDutyStatus): boolean =>
      newStatus === "OFF_DUTY" && hasPendingClinicalWork,
    [hasPendingClinicalWork],
  );

  const clearError = useCallback(() => setError(null), []);

  return { status, isChanging, error, changeStatus, needsConfirmation, clearError };
}
