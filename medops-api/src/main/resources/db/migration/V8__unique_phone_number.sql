ALTER TABLE patients.patient_profiles
    ADD CONSTRAINT uq_patient_profiles_phone_number UNIQUE (phone_number);

ALTER TABLE doctors.doctor_profiles
    ADD CONSTRAINT uq_doctor_profiles_phone_number UNIQUE (phone_number);
