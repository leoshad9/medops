package com.medops.assistant.domain;

import java.util.List;

/**
 * Aggregated LLM-safe context snapshot for the authenticated user.
 * Contains bounded projections of appointments, lab reports, prescriptions,
 * invoices, and medical records.
 */
public record AssistantContext(
        List<AssistantAppointment> appointments,
        List<AssistantLabReport> labReports,
        List<AssistantPrescription> prescriptions,
        List<AssistantInvoice> invoices,
        List<AssistantMedicalRecord> medicalRecords) {

    public static AssistantContext empty() {
        return new AssistantContext(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public static AssistantContext of(
            List<AssistantAppointment> appointments,
            List<AssistantLabReport> labReports,
            List<AssistantPrescription> prescriptions,
            List<AssistantInvoice> invoices,
            List<AssistantMedicalRecord> medicalRecords) {
        return new AssistantContext(
                appointments != null ? appointments : List.of(),
                labReports != null ? labReports : List.of(),
                prescriptions != null ? prescriptions : List.of(),
                invoices != null ? invoices : List.of(),
                medicalRecords != null ? medicalRecords : List.of());
    }
}
