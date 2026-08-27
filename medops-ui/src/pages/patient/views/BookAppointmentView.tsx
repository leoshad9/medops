import { useState } from "react";
import type { SubmitEvent } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, Send, Sparkles } from "lucide-react";

import { PATIENT_PATHS } from "../../../lib/patientRoutes";

export function BookAppointmentView() {
  const [department, setDepartment] = useState("");
  const [doctor, setDoctor] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [reason, setReason] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e: SubmitEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  const handleReset = () => {
    setDepartment("");
    setDoctor("");
    setDate("");
    setTime("");
    setReason("");
    setSubmitted(false);
  };

  return (
    <div className="max-w-2xl space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-6">
          <div className="flex items-center gap-2 text-brand-primary font-semibold text-xs tracking-wider uppercase">
            <Sparkles className="h-3.5 w-3.5" />
            <span>Online Scheduling</span>
          </div>
          <h2 className="text-xl font-bold text-brand-ink mt-1">Request a New Appointment</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Select your preferred specialist and time slot. Our scheduling coordinator will confirm your visit.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="dept-select" className="text-xs font-semibold text-brand-ink">
                Department <span className="text-brand-rust">*</span>
              </label>
              <select
                id="dept-select"
                required
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">Select department</option>
                <option value="Cardiology">Cardiology</option>
                <option value="Dermatology">Dermatology</option>
                <option value="General Medicine">General Medicine</option>
                <option value="Orthopedics">Orthopedics</option>
                <option value="Pediatrics">Pediatrics</option>
                <option value="ENT">ENT</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="doctor-select" className="text-xs font-semibold text-brand-ink">
                Preferred Doctor
              </label>
              <select
                id="doctor-select"
                value={doctor}
                onChange={(e) => setDoctor(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">No preference (First available)</option>
                <option value="Dr. Sarah Khan">Dr. Sarah Khan (Cardiology)</option>
                <option value="Dr. Ahmed Ali">Dr. Ahmed Ali (General Medicine)</option>
                <option value="Dr. Priya Mehta">Dr. Priya Mehta (Dermatology)</option>
                <option value="Dr. Vikram Singh">Dr. Vikram Singh (Orthopedics)</option>
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="date-input" className="text-xs font-semibold text-brand-ink">
                Preferred Date <span className="text-brand-rust">*</span>
              </label>
              <input
                id="date-input"
                type="date"
                required
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="time-select" className="text-xs font-semibold text-brand-ink">
                Preferred Time Slot <span className="text-brand-rust">*</span>
              </label>
              <select
                id="time-select"
                required
                value={time}
                onChange={(e) => setTime(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">Select time slot</option>
                <option value="9:00 AM">9:00 AM (Morning)</option>
                <option value="10:30 AM">10:30 AM (Morning)</option>
                <option value="1:00 PM">1:00 PM (Afternoon)</option>
                <option value="3:30 PM">3:30 PM (Evening)</option>
              </select>
            </div>
          </div>

          <div className="space-y-1.5 pt-1">
            <label htmlFor="reason-input" className="text-xs font-semibold text-brand-ink">
              Reason for Visit
            </label>
            <textarea
              id="reason-input"
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Briefly describe your symptoms or reason for the consultation (e.g. routine check-up, chest discomfort, skin rash)..."
              className="w-full rounded-xl border border-brand-line bg-white p-3.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
            />
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              className="flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs"
            >
              <Send className="h-4 w-4" />
              <span>Request Appointment</span>
            </button>
            <button
              type="button"
              onClick={handleReset}
              className="rounded-xl border border-brand-line bg-white px-5 py-2.5 text-sm font-semibold text-brand-muted transition hover:bg-brand-paper hover:text-brand-ink cursor-pointer"
            >
              Clear
            </button>
          </div>

          {/* Submission confirmation banner */}
          {submitted && (
            <div className="mt-4 flex items-start gap-3 rounded-xl border border-brand-success/30 bg-brand-success-tint p-4 text-brand-success animate-in fade-in slide-in-from-top-2">
              <CheckCircle2 className="h-5 w-5 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="text-sm font-bold">Appointment Request Sent</p>
                <p className="text-xs text-brand-success/90">
                  We have received your appointment request for <strong>{department}</strong> on{" "}
                  <strong>{date || "your requested date"}</strong> at <strong>{time}</strong>. Our care coordinator will contact you shortly to confirm.
                </p>
                <div className="pt-2">
                  <Link
                    to={PATIENT_PATHS.appointments}
                    className="text-xs font-bold text-brand-success underline hover:opacity-80 cursor-pointer"
                  >
                    Go to Appointments &rarr;
                  </Link>
                </div>
              </div>
            </div>
          )}
        </form>
      </section>
    </div>
  );
}
