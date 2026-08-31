import { useEffect, useMemo, useState, type SubmitEvent } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, Send, Sparkles } from "lucide-react";

import { clinicTodayYmd, formatClinicTime } from "../../../lib/clinicTime";
import { PATIENT_PATHS } from "../../../lib/patientRoutes";
import {
  bookAppointment,
  listDoctorSlots,
  listDoctors,
  type DoctorSummary,
} from "../../../services/appointmentService";

export function BookAppointmentView() {
  const [doctors, setDoctors] = useState<DoctorSummary[]>([]);
  const [specialty, setSpecialty] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [date, setDate] = useState(clinicTodayYmd());
  const [startsAt, setStartsAt] = useState("");
  const [slots, setSlots] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [loadingDoctors, setLoadingDoctors] = useState(true);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [booked, setBooked] = useState(false);

  const specialties = useMemo(() => {
    return Array.from(new Set(doctors.map((doctor) => doctor.specialty))).sort((a, b) => a.localeCompare(b));
  }, [doctors]);

  const visibleDoctors = specialty
    ? doctors.filter((doctor) => doctor.specialty === specialty)
    : doctors;

  useEffect(() => {
    let cancelled = false;
    setLoadingDoctors(true); // oxlint-disable-line react/set-state-in-effect
    listDoctors()
      .then((result) => {
        if (!cancelled) {
          setDoctors(result);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Unable to load doctors.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDoctors(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (doctorId && !visibleDoctors.some((doctor) => doctor.id === doctorId)) {
      setDoctorId(""); // oxlint-disable-line react/set-state-in-effect
      setStartsAt("");
      setSlots([]);
    }
  }, [doctorId, visibleDoctors]);

  useEffect(() => {
    if (!doctorId || !date) {
      setSlots([]); // oxlint-disable-line react/set-state-in-effect
      setStartsAt("");
      return;
    }
    let cancelled = false;
    setLoadingSlots(true);
    listDoctorSlots(doctorId, date)
      .then((result) => {
        if (!cancelled) {
          setSlots(result);
          setStartsAt("");
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setSlots([]);
          setError(err instanceof Error ? err.message : "Unable to load available times.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSlots(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [doctorId, date]);

  const handleSubmit = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await bookAppointment({ doctorId, startsAt, reason });
      setBooked(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Unable to book that appointment.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    setSpecialty("");
    setDoctorId("");
    setDate(clinicTodayYmd());
    setStartsAt("");
    setReason("");
    setBooked(false);
    setError(null);
  };

  return (
    <div className="max-w-2xl space-y-6">
      <section className="rounded-2xl border border-brand-line bg-white p-6 shadow-xs">
        <div className="border-b border-brand-line pb-4 mb-6">
          <div className="flex items-center gap-2 text-brand-primary font-semibold text-xs tracking-wider uppercase">
            <Sparkles className="h-3.5 w-3.5" />
            <span>Online Scheduling</span>
          </div>
          <h2 className="text-xl font-bold text-brand-ink mt-1">Book a New Appointment</h2>
          <p className="text-xs text-brand-muted mt-0.5">
            Choose a specialist and an open slot. Your visit is confirmed as soon as you book.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="dept-select" className="text-xs font-semibold text-brand-ink">
                Specialty
              </label>
              <select
                id="dept-select"
                value={specialty}
                onChange={(e) => setSpecialty(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">All specialties</option>
                {specialties.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="doctor-select" className="text-xs font-semibold text-brand-ink">
                Doctor <span className="text-brand-rust">*</span>
              </label>
              <select
                id="doctor-select"
                required
                value={doctorId}
                onChange={(e) => setDoctorId(e.target.value)}
                disabled={loadingDoctors}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">{loadingDoctors ? "Loading doctors…" : "Select doctor"}</option>
                {visibleDoctors.map((doctor) => (
                  <option key={doctor.id} value={doctor.id}>
                    {doctor.fullName} ({doctor.specialty})
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label htmlFor="date-input" className="text-xs font-semibold text-brand-ink">
                Date <span className="text-brand-rust">*</span>
              </label>
              <input
                id="date-input"
                type="date"
                required
                min={clinicTodayYmd()}
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="time-select" className="text-xs font-semibold text-brand-ink">
                Time slot <span className="text-brand-rust">*</span>
              </label>
              <select
                id="time-select"
                required
                value={startsAt}
                onChange={(e) => setStartsAt(e.target.value)}
                disabled={!doctorId || loadingSlots}
                className="w-full rounded-xl border border-brand-line bg-white px-3.5 py-2.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
              >
                <option value="">
                  {loadingSlots ? "Loading slots…" : "Select time slot"}
                </option>
                {slots.map((slot) => (
                  <option key={slot} value={slot}>
                    {formatClinicTime(slot)}
                  </option>
                ))}
              </select>
              {doctorId && !loadingSlots && slots.length === 0 && (
                <p className="text-xs text-brand-muted">
                  No open slots on this date. Sundays are closed; weekdays run 9:00 AM–5:00 PM.
                </p>
              )}
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
              placeholder="Briefly describe your symptoms or reason for the consultation…"
              className="w-full rounded-xl border border-brand-line bg-white p-3.5 text-sm text-brand-ink focus:border-brand-primary focus:outline-hidden transition"
            />
          </div>

          {error && (
            <p className="rounded-xl border border-brand-rust/30 bg-brand-rust-tint px-4 py-3 text-sm text-brand-rust">
              {error}
            </p>
          )}

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={submitting || booked}
              className="flex items-center gap-2 rounded-xl bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-primary-dark cursor-pointer shadow-2xs disabled:opacity-60"
            >
              <Send className="h-4 w-4" />
              <span>{submitting ? "Booking…" : "Book Appointment"}</span>
            </button>
            <button
              type="button"
              onClick={handleReset}
              className="rounded-xl border border-brand-line bg-white px-5 py-2.5 text-sm font-semibold text-brand-muted transition hover:bg-brand-paper hover:text-brand-ink cursor-pointer"
            >
              Clear
            </button>
          </div>

          {booked && (
            <div className="mt-4 flex items-start gap-3 rounded-xl border border-brand-success/30 bg-brand-success-tint p-4 text-brand-success">
              <CheckCircle2 className="h-5 w-5 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="text-sm font-bold">Appointment booked</p>
                <p className="text-xs text-brand-success/90">
                  Your visit is confirmed. You can reschedule or cancel from Appointments until it starts.
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
