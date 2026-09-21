-- Supports AppointmentReconciliationService, which transitions ended BOOKED
-- appointments to COMPLETED. That pass seeks by (status, ends_at); without this
-- index every run scans the full appointments table instead of seeking the slice.
CREATE INDEX idx_appointments_status_ends_at ON appointments.appointments (status, ends_at);
