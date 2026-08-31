package com.medops.prescriptions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.prescriptions.api.dto.CreatePrescriptionRequest;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.domain.PrescriptionStatus;
import com.medops.prescriptions.infrastructure.Prescription;
import com.medops.prescriptions.infrastructure.PrescriptionRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;

@ExtendWith(MockitoExtension.class)
class CreatePrescriptionServiceTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private ClinicalAccessService clinicalAccess;
    @Mock
    private PrescriptionAssembler assembler;
    @Mock
    private AuditService auditService;

    private CreatePrescriptionService service;
    private DoctorProfile doctor;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        service = new CreatePrescriptionService(
                prescriptionRepository, clinicalAccess, assembler, auditService);
        patientId = UUID.randomUUID();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr. Test")
                .build();
    }

    @Test
    void createSavesActivePrescriptionWithoutFile() {
        when(clinicalAccess.requireTreatingDoctor("doctor.test@medops.dev", patientId)).thenReturn(doctor);
        when(prescriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assembler.toResponse(any())).thenReturn(new PrescriptionResponse(
                UUID.randomUUID(), patientId, doctor.getId(), "Pat", "MRN-1", "Dr. Test",
                "Amoxicillin", "500mg", "Twice daily", 2, PrescriptionStatus.ACTIVE, null, false, null));

        service.create(
                "doctor.test@medops.dev",
                patientId,
                new CreatePrescriptionRequest("Amoxicillin", "500mg", "Twice daily", 2));

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(captor.getValue().getStorageKey()).isNull();
        verify(auditService).recordEvent(
                AuditEventType.PRESCRIPTION_CREATED, doctor.getUserId(), "doctor.test@medops.dev");
    }
}
