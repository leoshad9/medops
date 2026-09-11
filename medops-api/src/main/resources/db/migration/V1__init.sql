CREATE SCHEMA appointments;
CREATE SCHEMA audit;
CREATE SCHEMA auth;
CREATE SCHEMA billing;
CREATE SCHEMA doctors;
CREATE SCHEMA notifications;
CREATE SCHEMA patients;
CREATE SCHEMA prescriptions;
CREATE SCHEMA reports;
CREATE SCHEMA user_management;

CREATE TABLE appointments.appointments (
    id uuid NOT NULL,
    cancelled_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    doctor_profile_id uuid NOT NULL,
    ends_at timestamp(6) with time zone NOT NULL,
    location character varying(255),
    patient_profile_id uuid NOT NULL,
    reason character varying(500),
    starts_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT appointments_status_check CHECK (((status)::text = ANY ((ARRAY['BOOKED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying])::text[])))
);

CREATE TABLE audit.audit_events (
    id uuid NOT NULL,
    correlation_id character varying(100),
    event_type character varying(100) NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    subject_email character varying(255),
    subject_id uuid,
    CONSTRAINT audit_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['AUTH_REGISTER'::character varying, 'AUTH_LOGIN_SUCCESS'::character varying, 'AUTH_LOGIN_FAILURE'::character varying, 'AUTH_LOGIN_LOCKED'::character varying, 'AUTH_TOKEN_REFRESH_SUCCESS'::character varying, 'AUTH_TOKEN_REFRESH_FAILURE'::character varying, 'AUTH_LOGOUT'::character varying, 'APPOINTMENT_BOOKED'::character varying, 'APPOINTMENT_CANCELLED'::character varying, 'APPOINTMENT_RESCHEDULED'::character varying, 'APPOINTMENT_COMPLETED'::character varying, 'REPORT_UPLOADED'::character varying, 'REPORT_REVIEWED'::character varying, 'REPORT_VIEWED'::character varying, 'REPORT_SUMMARIZED'::character varying, 'PRESCRIPTION_CREATED'::character varying, 'INVOICE_CREATED'::character varying, 'INVOICE_ISSUED'::character varying, 'INVOICE_VOIDED'::character varying, 'PAYMENT_RECORDED'::character varying, 'PAYMENT_REFUNDED'::character varying])::text[])))
);

CREATE TABLE auth.refresh_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    token_hash character varying(255) NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE auth.roles (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(100) NOT NULL
);

CREATE TABLE auth.user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE billing.invoice_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255) NOT NULL,
    quantity integer NOT NULL,
    unit_price_cents bigint NOT NULL,
    invoice_id uuid NOT NULL
);

CREATE TABLE billing.invoices (
    id uuid NOT NULL,
    appointment_id uuid,
    created_at timestamp(6) with time zone NOT NULL,
    due_date date,
    issued_at timestamp(6) with time zone,
    notes character varying(500),
    patient_profile_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT invoices_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ISSUED'::character varying, 'PARTIALLY_PAID'::character varying, 'PAID'::character varying, 'VOID'::character varying])::text[])))
);

CREATE TABLE billing.payments (
    id uuid NOT NULL,
    amount_cents bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    idempotency_key character varying(64) NOT NULL,
    method character varying(30) NOT NULL,
    paid_at timestamp(6) with time zone,
    reference character varying(255),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    invoice_id uuid NOT NULL,
    CONSTRAINT payments_method_check CHECK (((method)::text = ANY ((ARRAY['CASH'::character varying, 'CARD'::character varying, 'INSURANCE'::character varying, 'BANK_TRANSFER'::character varying])::text[]))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);

CREATE TABLE doctors.doctor_profiles (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    full_name character varying(255) NOT NULL,
    license_number character varying(100) NOT NULL,
    phone_number character varying(20) NOT NULL,
    specialty character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE notifications.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message text NOT NULL,
    is_read boolean NOT NULL,
    read_at timestamp(6) with time zone,
    reference_id uuid,
    reference_type character varying(50),
    source_event_id uuid,
    title character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['APPOINTMENT_BOOKED'::character varying, 'REPORT_UPLOADED'::character varying])::text[])))
);

CREATE TABLE patients.patient_profiles (
    id uuid NOT NULL,
    address character varying(255),
    blood_group character varying(10),
    created_at timestamp(6) with time zone NOT NULL,
    date_of_birth date NOT NULL,
    emergency_contact character varying(255),
    full_name character varying(255) NOT NULL,
    gender character varying(20) NOT NULL,
    insurance_policy_number character varying(100),
    insurance_provider character varying(100),
    mrn character varying(40) NOT NULL,
    phone_number character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT patient_profiles_gender_check CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying])::text[])))
);

CREATE TABLE prescriptions.prescriptions (
    id uuid NOT NULL,
    content_type character varying(100),
    created_at timestamp(6) with time zone NOT NULL,
    doctor_profile_id uuid NOT NULL,
    dosage character varying(200) NOT NULL,
    instructions character varying(1000) NOT NULL,
    medication_name character varying(200) NOT NULL,
    original_filename character varying(255),
    patient_profile_id uuid NOT NULL,
    refills_remaining integer NOT NULL,
    size_bytes bigint,
    status character varying(20) NOT NULL,
    storage_key character varying(255),
    CONSTRAINT prescriptions_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISCONTINUED'::character varying])::text[])))
);

CREATE TABLE reports.clinical_reports (
    id uuid NOT NULL,
    content_type character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    doctor_profile_id uuid NOT NULL,
    notes character varying(1000),
    original_filename character varying(255) NOT NULL,
    patient_profile_id uuid NOT NULL,
    reviewed_at timestamp(6) with time zone,
    size_bytes bigint NOT NULL,
    status character varying(20) NOT NULL,
    storage_key character varying(255) NOT NULL,
    summarized_at timestamp(6) with time zone,
    summary text,
    title character varying(200) NOT NULL,
    CONSTRAINT clinical_reports_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'REVIEWED'::character varying])::text[])))
);

CREATE TABLE user_management.users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    status character varying(50) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOCKED'::character varying, 'DISABLED'::character varying])::text[])))
);

ALTER TABLE ONLY appointments.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY audit.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY auth.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT uko2mlirhldriil2y7krapq4frt UNIQUE (token_hash);

ALTER TABLE ONLY auth.roles
    ADD CONSTRAINT ukofx66keruapi6vyqpv6f2or37 UNIQUE (name);

ALTER TABLE ONLY auth.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY billing.payments
    ADD CONSTRAINT idx_payments_idempotency_key UNIQUE (idempotency_key);

ALTER TABLE ONLY billing.invoice_items
    ADD CONSTRAINT invoice_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY billing.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY billing.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY doctors.doctor_profiles
    ADD CONSTRAINT doctor_profiles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY doctors.doctor_profiles
    ADD CONSTRAINT ukf2ac4saatw7tnup2kqa53oqkl UNIQUE (user_id);

ALTER TABLE ONLY doctors.doctor_profiles
    ADD CONSTRAINT uklfhrhro6r3sajly4jo7sfmsvw UNIQUE (license_number);

ALTER TABLE ONLY notifications.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notifications.notifications
    ADD CONSTRAINT uk_notifications_user_source_event UNIQUE (user_id, source_event_id);

ALTER TABLE ONLY patients.patient_profiles
    ADD CONSTRAINT patient_profiles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY patients.patient_profiles
    ADD CONSTRAINT ukm1vq601k5agscsnei45j7bcv1 UNIQUE (user_id);

ALTER TABLE ONLY patients.patient_profiles
    ADD CONSTRAINT ukp33q81w6y670n23vn7nt92eg7 UNIQUE (mrn);

ALTER TABLE ONLY prescriptions.prescriptions
    ADD CONSTRAINT prescriptions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY reports.clinical_reports
    ADD CONSTRAINT clinical_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY user_management.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);

ALTER TABLE ONLY user_management.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_appointments_doctor ON appointments.appointments USING btree (doctor_profile_id);
CREATE INDEX idx_appointments_doctor_start ON appointments.appointments USING btree (doctor_profile_id, starts_at);
CREATE INDEX idx_appointments_patient ON appointments.appointments USING btree (patient_profile_id);
CREATE INDEX idx_audit_events_event_type ON audit.audit_events USING btree (event_type);
CREATE INDEX idx_audit_events_occurred_at ON audit.audit_events USING btree (occurred_at);
CREATE INDEX idx_audit_events_subject_id ON audit.audit_events USING btree (subject_id);
CREATE INDEX idx_refresh_tokens_expires_at ON auth.refresh_tokens USING btree (expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON auth.refresh_tokens USING btree (revoked_at);
CREATE INDEX idx_refresh_tokens_token_hash ON auth.refresh_tokens USING btree (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON auth.refresh_tokens USING btree (user_id);
CREATE INDEX idx_roles_name ON auth.roles USING btree (name);
CREATE INDEX idx_invoice_items_invoice ON billing.invoice_items USING btree (invoice_id);
CREATE INDEX idx_invoices_appointment ON billing.invoices USING btree (appointment_id);
CREATE INDEX idx_invoices_patient ON billing.invoices USING btree (patient_profile_id);
CREATE INDEX idx_invoices_status ON billing.invoices USING btree (status);
CREATE INDEX idx_payments_invoice ON billing.payments USING btree (invoice_id);
CREATE INDEX idx_doctor_profiles_license_number ON doctors.doctor_profiles USING btree (license_number);
CREATE INDEX idx_doctor_profiles_user_id ON doctors.doctor_profiles USING btree (user_id);
CREATE INDEX idx_notifications_user_created ON notifications.notifications USING btree (user_id, created_at);
CREATE INDEX idx_notifications_user_unread ON notifications.notifications USING btree (user_id, is_read);
CREATE INDEX idx_patient_profiles_mrn ON patients.patient_profiles USING btree (mrn);
CREATE INDEX idx_patient_profiles_user_id ON patients.patient_profiles USING btree (user_id);
CREATE INDEX idx_prescriptions_doctor ON prescriptions.prescriptions USING btree (doctor_profile_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions.prescriptions USING btree (patient_profile_id);
CREATE INDEX idx_reports_doctor ON reports.clinical_reports USING btree (doctor_profile_id);
CREATE INDEX idx_reports_patient ON reports.clinical_reports USING btree (patient_profile_id);
CREATE INDEX idx_users_created_at ON user_management.users USING btree (created_at);
CREATE INDEX idx_users_email ON user_management.users USING btree (email);
CREATE INDEX idx_users_status ON user_management.users USING btree (status);

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES user_management.users(id);

ALTER TABLE ONLY auth.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES auth.roles(id);

ALTER TABLE ONLY auth.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES user_management.users(id);

ALTER TABLE ONLY billing.invoice_items
    ADD CONSTRAINT fk46ae0lhu1oqs7cv91fn6y9n7w FOREIGN KEY (invoice_id) REFERENCES billing.invoices(id);

ALTER TABLE ONLY billing.payments
    ADD CONSTRAINT fkrbqec6be74wab8iifh8g3i50i FOREIGN KEY (invoice_id) REFERENCES billing.invoices(id);
