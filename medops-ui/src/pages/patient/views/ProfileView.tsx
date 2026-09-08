import { useState } from "react";
import type { SubmitEvent } from "react";
import { Edit3 } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";
import { displayGender, formatClinicDate } from "../../../lib/clinicTime";
import { updateMyProfile } from "../../../services/patientService";
import type { PatientDetailedProfile, PatientProfile } from "../../../types/patient";

export function ProfileView() {
  const { data, profile } = usePatientPortal();
  const resolvedProfile = profile ?? data.profile;
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState<PatientDetailedProfile>(
    () => buildDetailedProfile(resolvedProfile, data.detailedProfile),
  );
  const [savedProfile, setSavedProfile] = useState<PatientProfile | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Read-only display view, derived on every render so it stays in sync with the
  // /patients/me response even when that fetch resolves after this view mounts.
  const displayed = isEditing
    ? formData
    : buildDetailedProfile(savedProfile ?? resolvedProfile, data.detailedProfile);

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

  const startEditing = () => {
    setFormData(buildDetailedProfile(savedProfile ?? resolvedProfile, data.detailedProfile));
    setSaveError(null);
    setSaveSuccess(false);
    setIsEditing(true);
  };

  const cancelEditing = () => {
    setIsEditing(false);
    setSaveError(null);
    setSaveSuccess(false);
  };

  const handleSave = async (e: SubmitEvent) => {
    e.preventDefault();
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const updated = await updateMyProfile({
        fullName: formData.fullName,
        phoneNumber: formData.phone,
        bloodGroup: formData.bloodGroup,
        address: formData.address,
        emergencyContact: formData.emergencyContact,
        insuranceProvider: formData.insuranceProvider,
        insurancePolicyNumber: formData.insurancePolicyNumber,
      });
      setSavedProfile(updated);
      setIsEditing(false);
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "Unable to save your profile. Please try again.");
    }
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
          <button
            type="button"
            onClick={startEditing}
            className="flex items-center gap-1.5 rounded-lg border border-brand-primary bg-white px-3.5 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary-tint cursor-pointer"
          >
            <Edit3 className="h-3.5 w-3.5" />
            <span>Edit Profile</span>
          </button>
        </div>

        {saveSuccess && (
          <p className="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 px-3.5 py-2 text-xs font-medium text-emerald-700">
            Profile details updated successfully.
          </p>
        )}
        {saveError && (
          <p className="mt-3 rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-3.5 py-2 text-xs font-medium text-brand-rust">
            {saveError}
          </p>
        )}

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

        {isEditing && (
          <form onSubmit={handleSave} className="mt-6 space-y-5">
            <h3 className="text-sm font-bold text-brand-ink mb-4">Edit Contact Information</h3>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <label htmlFor="profile-full-name" className="text-xs font-semibold text-brand-ink">
                  Full Name
                </label>
                <input
                  id="profile-full-name"
                  type="text"
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>

              <div className="space-y-1">
                <label htmlFor="profile-mrn" className="text-xs font-semibold text-brand-ink">
                  MRN
                </label>
                <input
                  id="profile-mrn"
                  type="text"
                  value={formData.mrn}
                  disabled
                  onChange={(e) => setFormData({ ...formData, mrn: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm font-brand-mono text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <label htmlFor="profile-dob" className="text-xs font-semibold text-brand-ink">
                  Date of Birth
                </label>
                <input
                  id="profile-dob"
                  type="text"
                  value={formData.dateOfBirth}
                  disabled
                  onChange={(e) => setFormData({ ...formData, dateOfBirth: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm font-brand-mono text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>

              <div className="space-y-1">
                <label htmlFor="profile-gender" className="text-xs font-semibold text-brand-ink">
                  Gender
                </label>
                <input
                  id="profile-gender"
                  type="text"
                  value={formData.gender}
                  disabled
                  onChange={(e) => setFormData({ ...formData, gender: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>

              <div className="space-y-1">
                <label htmlFor="profile-blood-group" className="text-xs font-semibold text-brand-ink">
                  Blood Group
                </label>
                <input
                  id="profile-blood-group"
                  type="text"
                  value={formData.bloodGroup}
                  onChange={(e) => setFormData({ ...formData, bloodGroup: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm font-brand-mono text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>

              <div className="space-y-1">
                <label htmlFor="profile-phone" className="text-xs font-semibold text-brand-ink">
                  Phone
                </label>
                <input
                  id="profile-phone"
                  type="text"
                  value={formData.phone}
                  onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                  className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
                />
              </div>
            </div>

            <div className="space-y-1">
              <label htmlFor="profile-email" className="text-xs font-semibold text-brand-ink">
                Email
              </label>
              <input
                id="profile-email"
                type="email"
                value={formData.email}
                  disabled
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="profile-address" className="text-xs font-semibold text-brand-ink">
                Residential Address
              </label>
              <textarea
                id="profile-address"
                rows={2}
                value={formData.address}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="profile-emergency-contact" className="text-xs font-semibold text-brand-ink">
                Emergency Contact
              </label>
              <input
                id="profile-emergency-contact"
                type="text"
                value={formData.emergencyContact}
                onChange={(e) => setFormData({ ...formData, emergencyContact: e.target.value })}
                className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
              />
            </div>

            <div className="flex justify-end gap-2 pt-4 border-t border-brand-line">
              <button
                type="button"
                onClick={cancelEditing}
                className="rounded-lg border border-brand-line px-4 py-2 text-xs font-semibold text-brand-ink hover:bg-brand-paper cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-brand-primary px-4 py-2 text-xs font-semibold text-white hover:bg-brand-primary-dark cursor-pointer shadow-2xs"
              >
                Save Changes
              </button>
            </div>
          </form>
        )}

        {!isEditing && (
          <div className="mt-6 pt-4 border-t border-brand-line">
            <p className="text-xs text-brand-muted">
              Insurance Provider: {displayed.insuranceProvider} &middot; Policy: {displayed.insurancePolicyNumber}
            </p>
          </div>
        )}
      </section>
    </div>
  );
}