-- Makes the database the arbiter of doctor-slot exclusivity.
-- BookAppointmentService maps a DataIntegrityViolationException from a unique
-- index onto the same 409 as its pre-checks, and says so in the catch block
-- ("the unique index is the arbiter"). That index did not exist: the only
-- appointments indexes were V1's primary key and V4's non-unique
-- (status, ends_at). Both booking pre-checks are unlocked read-then-write, so
-- two transactions could each read an empty slot and both commit, double-booking
-- one doctor. AppointmentBookingConcurrencyIT reproduced that on every run.
--
-- Partial on status so CANCELLED and COMPLETED history does not block rebooking
-- a slot that was freed or has already ended.

-- Refuse to apply rather than silently pick a winner if the bug above already
-- left duplicates behind; this table holds real clinical bookings. Diagnose with:
--   SELECT doctor_profile_id, starts_at, count(*)
--   FROM appointments.appointments
--   WHERE status = 'BOOKED'
--   GROUP BY doctor_profile_id, starts_at
--   HAVING count(*) > 1;
DO $$
DECLARE
    duplicate_slots bigint;
BEGIN
    SELECT count(*) INTO duplicate_slots
    FROM (
        SELECT 1
        FROM appointments.appointments
        WHERE status = 'BOOKED'
        GROUP BY doctor_profile_id, starts_at
        HAVING count(*) > 1
    ) duplicates;

    IF duplicate_slots > 0 THEN
        RAISE EXCEPTION
            'duplicate BOOKED appointments found for % doctor/start-time slot(s); resolve them before this index can be created',
            duplicate_slots;
    END IF;
END
$$;

CREATE UNIQUE INDEX uk_appointments_doctor_slot_booked
    ON appointments.appointments (doctor_profile_id, starts_at)
    WHERE status = 'BOOKED';