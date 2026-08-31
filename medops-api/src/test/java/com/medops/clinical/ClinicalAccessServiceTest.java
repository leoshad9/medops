package com.medops.clinical;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

/**
 * Resource-level authorization (BOLA/IDOR) at the clinical access policy.
 * A valid authenticated principal is not enough — ownership / care relationship must hold.
 */
@ExtendWith(MockitoExtension.class)
class ClinicalAccessServiceTest {

    private static final String DOCTOR_EMAIL = "doctor.a@medops.dev";
    private static final String PATIENT_EMAIL = "patient.a@medops.dev";

    @Mock
    private CareRelationship careRelationship;
    @Mock
    private AppointmentActorResolver actorResolver;
    @Mock
    private PatientProfileRepository patientProfileRepository;

    private ClinicalAccessService access;
    private DoctorProfile doctorA;
    private PatientProfile patientA;
    private PatientProfile patientB;

    @BeforeEach
    void setUp() {
        access = new ClinicalAccessService(careRelationship, actorResolver, patientProfileRepository);
        doctorA = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .fullName("Dr A")
                .build();
        patientA = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .mrn("MRN-A")
                .fullName("Patient A")
                .build();
        patientB = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .mrn("MRN-B")
                .fullName("Patient B")
                .build();
    }

    @Test
    void requireTreatingDoctorAllowsWhenCareRelationshipExists() {
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctorA);
        when(patientProfileRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(careRelationship.doctorMayTreat(doctorA.getId(), patientA.getId())).thenReturn(true);

        assertDoesNotThrow(() -> access.requireTreatingDoctor(DOCTOR_EMAIL, patientA.getId()));
    }

    @Test
    void requireTreatingDoctorDeniesWhenDoctorHasNoRelationshipToPatient() {
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctorA);
        when(patientProfileRepository.findById(patientB.getId())).thenReturn(Optional.of(patientB));
        when(careRelationship.doctorMayTreat(doctorA.getId(), patientB.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> access.requireTreatingDoctor(DOCTOR_EMAIL, patientB.getId()));
        verify(careRelationship).doctorMayTreat(doctorA.getId(), patientB.getId());
    }

    @Test
    void assertPatientOwnsAllowsOwner() {
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patientA);

        assertDoesNotThrow(() -> access.assertPatientOwns(patientA.getId(), PATIENT_EMAIL));
    }

    @Test
    void assertPatientOwnsDeniesOtherPatientsId() {
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patientA);

        assertThrows(AccessDeniedException.class,
                () -> access.assertPatientOwns(patientB.getId(), PATIENT_EMAIL));
    }

    @Test
    void assertDoctorOwnsDeniesOtherDoctorsProfileId() {
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctorA);
        UUID otherDoctorId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> access.assertDoctorOwns(otherDoctorId, DOCTOR_EMAIL));
    }

    @Test
    void requireReportReaderAllowsOwningPatient() {
        when(actorResolver.findDoctor(PATIENT_EMAIL)).thenReturn(Optional.empty());
        when(actorResolver.requirePatient(PATIENT_EMAIL)).thenReturn(patientA);

        assertEquals(patientA.getUserId(), access.requireReportReader(PATIENT_EMAIL, patientA.getId()));
    }

    @Test
    void requireReportReaderAllowsTreatingDoctor() {
        when(actorResolver.findDoctor(DOCTOR_EMAIL)).thenReturn(Optional.of(doctorA));
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctorA);
        when(patientProfileRepository.findById(patientA.getId())).thenReturn(Optional.of(patientA));
        when(careRelationship.doctorMayTreat(doctorA.getId(), patientA.getId())).thenReturn(true);

        assertEquals(doctorA.getUserId(), access.requireReportReader(DOCTOR_EMAIL, patientA.getId()));
        verify(actorResolver, never()).requirePatient(DOCTOR_EMAIL);
    }

    @Test
    void requireReportReaderDeniesDoctorWithoutCareRelationship() {
        when(actorResolver.findDoctor(DOCTOR_EMAIL)).thenReturn(Optional.of(doctorA));
        when(actorResolver.requireDoctor(DOCTOR_EMAIL)).thenReturn(doctorA);
        when(patientProfileRepository.findById(patientB.getId())).thenReturn(Optional.of(patientB));
        when(careRelationship.doctorMayTreat(doctorA.getId(), patientB.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> access.requireReportReader(DOCTOR_EMAIL, patientB.getId()));
        verify(actorResolver, never()).requirePatient(DOCTOR_EMAIL);
    }
}
