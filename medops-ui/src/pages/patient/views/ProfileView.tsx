import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import { displayGender, formatClinicDate } from "../../../lib/clinicTime";
import type { PatientDetailedProfile, PatientProfile } from "../../../types/patient";

export function ProfileView() {
  const { data, profile } = usePatientPortal();
  const resolvedProfile = profile ?? data.profile;

  // Read-only display view, derived on every render so it stays in sync with the
  // /patients/me response even when that fetch resolves after this view mounts.
  const displayed = buildDetailedProfile(resolvedProfile, data.detailedProfile);

  function buildDetailedProfile(loggedIn: PatientProfile, fallback: PatientDetailedProfile): PatientDetailedProfile {
    return {
      fullName: valueOf(loggedIn.name, fallback.fullName),
      mrn: valueOf(loggedIn.mrn, fallback.mrn),
      dateOfBirth: formatClinicDate(valueOf(loggedIn.dateOfBirth, fallback.dateOfBirth)),
      gender: displayGender(valueOf(loggedIn.gender, fallback.gender)),
      bloodGroup: valueOf(loggedIn.bloodGroup, fallback.bloodGroup),
      phone: valueOf(loggedIn.phoneNumber, fallback.phone),
      email: valueOf(loggedIn.email, fallback.email),
      address: valueOf(loggedIn.address, fallback.address),
      emergencyContact: valueOf(loggedIn.emergencyContact, fallback.emergencyContact),
      insuranceProvider: valueOf(loggedIn.insuranceProvider, fallback.insuranceProvider),
      insurancePolicyNumber: valueOf(loggedIn.insurancePolicyNumber, fallback.insurancePolicyNumber),
    };
  }

  function valueOf<T>(primary: T | undefined | null, fallback: T): T {
    return primary === undefined || primary === null || primary === "" ? fallback : primary;
  }

  return (
    <div className="max-w-3xl space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-6">
          <div>
            <h2 className="text-lg font-bold text-brand-ink">Personal & Medical Information</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Verified clinical identity and insurance policy information on file with MedOps.
            </p>
          </div>
        </div>

        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Full Name</dt>
            <dd className="text-sm font-semibold text-brand-ink">{displayed.fullName}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">MRN (Medical Record #)</dt>
            <dd className="font-brand-mono text-sm font-bold text-brand-primary-dark">{displayed.mrn}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Date of Birth</dt>
            <dd className="font-brand-mono text-sm font-medium text-brand-ink">{displayed.dateOfBirth}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Gender</dt>
            <dd className="text-sm font-medium text-brand-ink">{displayed.gender}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Blood Group</dt>
            <dd className="font-brand-mono text-sm font-bold text-brand-primary-dark">{displayed.bloodGroup}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Phone Number</dt>
            <dd className="font-brand-mono text-sm font-medium text-brand-ink">{displayed.phone}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Email Address</dt>
            <dd className="text-sm font-medium text-brand-ink">{displayed.email}</dd>
          </div>
        </dl>

        <div className="mt-6 pt-4 border-t border-brand-line">
          <p className="text-xs text-brand-muted">
            Insurance Provider: {displayed.insuranceProvider} &middot; Policy: {displayed.insurancePolicyNumber}
          </p>
        </div>
      </section>
    </div>
  );
}