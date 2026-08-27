import { useOutletContext } from "react-router-dom";

import type { PatientPortalContext } from "./PatientLayout";

export function usePatientPortal(): PatientPortalContext {
  return useOutletContext<PatientPortalContext>();
}
