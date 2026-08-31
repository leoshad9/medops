package com.medops.prescriptions.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.prescriptions.api.dto.CreatePrescriptionRequest;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.domain.PrescriptionStatus;
import com.medops.prescriptions.infrastructure.Prescription;
import com.medops.prescriptions.infrastructure.PrescriptionRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreatePrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAccessService clinicalAccess;
    private final PrescriptionAssembler assembler;
    private final AuditService auditService;

    @Transactional
    public PrescriptionResponse create(String doctorEmail, UUID patientId, CreatePrescriptionRequest request) {
        DoctorProfile doctor = clinicalAccess.requireTreatingDoctor(doctorEmail, patientId);

        Prescription saved = prescriptionRepository.save(Prescription.builder()
                .patientProfileId(patientId)
                .doctorProfileId(doctor.getId())
                .medicationName(request.medicationName().trim())
                .dosage(request.dosage().trim())
                .instructions(request.instructions().trim())
                .refillsRemaining(request.refillsRemaining())
                .status(PrescriptionStatus.ACTIVE)
                .build());

        auditService.recordEvent(AuditEventType.PRESCRIPTION_CREATED, doctor.getUserId(), doctorEmail);
        return assembler.toResponse(saved);
    }
}
