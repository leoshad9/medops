import { useState } from "react";
import type { SubmitEvent } from "react";
import { Edit3, Shield } from "lucide-react";

import { usePatientPortal } from "../../../components/patient/usePatientPortal";

export function ProfileView() {
  const { data } = usePatientPortal();
  const initialProfile = data.detailedProfile;
  const [profile, setProfile] = useState(initialProfile);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState(initialProfile);

  const handleSave = (e: SubmitEvent) => {
    e.preventDefault();
    setProfile(formData);
    setIsEditing(false);
    alert("Profile details updated successfully.");
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
            onClick={() => setIsEditing(true)}
            className="flex items-center gap-1.5 rounded-lg border border-brand-primary bg-white px-3.5 py-1.5 text-xs font-semibold text-brand-primary-dark transition hover:bg-brand-primary-tint cursor-pointer"
          >
            <Edit3 className="h-3.5 w-3.5" />
            <span>Edit Profile</span>
          </button>
        </div>

        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Full Name</dt>
            <dd className="text-sm font-semibold text-brand-ink">{profile.fullName}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">MRN (Medical Record #)</dt>
            <dd className="font-brand-mono text-sm font-bold text-brand-primary-dark">{profile.mrn}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Date of Birth</dt>
            <dd className="font-brand-mono text-sm font-medium text-brand-ink">{profile.dateOfBirth}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Gender</dt>
            <dd className="text-sm font-medium text-brand-ink">{profile.gender}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Blood Group</dt>
            <dd className="font-brand-mono text-sm font-bold text-brand-primary-dark">{profile.bloodGroup}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Phone Number</dt>
            <dd className="font-brand-mono text-sm font-medium text-brand-ink">{profile.phone}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Email Address</dt>
            <dd className="text-sm font-medium text-brand-ink">{profile.email}</dd>
          </div>

          <div className="space-y-1">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Emergency Contact</dt>
            <dd className="text-sm font-medium text-brand-ink">{profile.emergencyContact}</dd>
          </div>

          <div className="space-y-1 sm:col-span-2">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-muted">Residential Address</dt>
            <dd className="text-sm font-medium text-brand-ink">{profile.address}</dd>
          </div>

          <div className="space-y-1 sm:col-span-2 rounded-xl bg-brand-primary-tint/40 p-3.5 border border-brand-primary-tint">
            <dt className="text-xs font-bold uppercase tracking-wider text-brand-primary-dark flex items-center gap-1.5">
              <Shield className="h-3.5 w-3.5" />
              <span>Insurance Coverage</span>
            </dt>
            <dd className="text-sm font-semibold text-brand-ink mt-1">
              {profile.insuranceProvider} · Policy #{profile.insurancePolicyNumber}
            </dd>
          </div>
        </dl>
      </section>

      {/* Edit Profile Modal */}
      {isEditing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-brand-ink/40 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl border border-brand-line bg-white p-6 shadow-xl animate-in fade-in zoom-in-95 max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-bold text-brand-ink border-b border-brand-line pb-3">Edit Profile</h3>
            <form onSubmit={handleSave} className="mt-4 space-y-4 text-sm">
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

              <div className="grid grid-cols-2 gap-3">
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
                <div className="space-y-1">
                  <label htmlFor="profile-email" className="text-xs font-semibold text-brand-ink">
                    Email
                  </label>
                  <input
                    id="profile-email"
                    type="email"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                    className="w-full rounded-xl border border-brand-line px-3 py-2 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden"
                  />
                </div>
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
                  onClick={() => setIsEditing(false)}
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
          </div>
        </div>
      )}
    </div>
  );
}
