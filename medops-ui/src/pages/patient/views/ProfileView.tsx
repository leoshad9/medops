import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import { displayGender, formatClinicDate } from "../../../lib/clinicTime";

export function ProfileView() {
  const { profile } = usePatientPortal();

  if (!profile) {
    return (
      <div className="rounded-xl border border-brand-line bg-white p-6 shadow-xs">
        <p className="text-sm text-brand-muted">Loading your profile…</p>
      </div>
    );
  }

  // Read-only display view, derived on every render so it stays in sync with the
  // /patients/me response even when that fetch resolves after this view mounts.
  const displayed = {
    fullName: profile.name,
    mrn: profile.mrn,
    dateOfBirth: formatClinicDate(profile.dateOfBirth),
    gender: displayGender(profile.gender),
    bloodGroup: profile.bloodGroup ?? "Not on file",
    phone: profile.phoneNumber ?? "Not on file",
    email: profile.email ?? "Not on file",
    address: profile.address ?? "Not on file",
    emergencyContact: profile.emergencyContact ?? "Not on file",
    insuranceProvider: profile.insuranceProvider ?? "Not on file",
    insurancePolicyNumber: profile.insurancePolicyNumber ?? "Not on file",
  };

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