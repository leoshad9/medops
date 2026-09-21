import { useOutletContext } from "react-router-dom";

import type { DoctorLayoutContext } from "../../components/doctor/DoctorLayout";

export function DoctorProfileView() {
  const { profile } = useOutletContext<DoctorLayoutContext>();

  if (!profile) {
    return (
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <p className="text-sm text-brand-muted">Loading your profile…</p>
      </section>
    );
  }

  return (
    <div className="max-w-3xl space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="flex items-center justify-between border-b border-brand-line pb-4 mb-6">
          <div>
            <h2 className="text-lg font-bold text-brand-ink">Clinical Credentials &amp; Contact</h2>
            <p className="text-xs text-brand-muted mt-0.5">
              Your verified clinical identity on file with MedOps.
            </p>
          </div>
        </div>

        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Full Name</dt>
            <dd className="text-sm font-semibold text-brand-ink">{profile.name}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Specialty</dt>
            <dd className="text-sm font-semibold text-brand-ink">{profile.specialty}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">License Number</dt>
            <dd className="font-brand-mono text-sm font-bold text-brand-primary-dark">{profile.licenseNumber}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Email Address</dt>
            <dd className="text-sm font-medium text-brand-ink">{profile.email}</dd>
          </div>

          <div className="space-y-1 sm:col-span-2">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Phone Number</dt>
            <dd className="font-brand-mono text-sm font-medium text-brand-ink">{profile.phoneNumber}</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}