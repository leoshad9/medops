package com.medops.appointments.application;

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

import com.medops.auth.entity.User;
import com.medops.auth.entity.UserStatus;
import com.medops.auth.repository.UserRepository;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

@ExtendWith(MockitoExtension.class)
class AppointmentActorResolverTest {

    private static final String EMAIL = "actor@medops.dev";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    private AppointmentActorResolver resolver;
    private User user;
    private PatientProfile patient;
    private DoctorProfile doctor;

    @BeforeEach
    void setUp() {
        resolver = new AppointmentActorResolver(userRepository, patientProfileRepository, doctorProfileRepository);
        user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        patient = PatientProfile.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .mrn("MRN-1")
                .fullName("Pat")
                .build();
        doctor = DoctorProfile.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .fullName("Dr")
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void requireAppointmentPartyAllowsOwningPatient() {
        when(doctorProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(patientProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(patient));

        UUID actorUserId = resolver.requireAppointmentParty(EMAIL, patient.getId(), UUID.randomUUID());

        assertEquals(user.getId(), actorUserId);
    }

    @Test
    void requireAppointmentPartyDeniesOtherPatientsAppointment() {
        when(doctorProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(patientProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(patient));

        assertThrows(AccessDeniedException.class,
                () -> resolver.requireAppointmentParty(EMAIL, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void requireAppointmentPartyAllowsAssignedDoctor() {
        when(doctorProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(doctor));

        UUID actorUserId = resolver.requireAppointmentParty(EMAIL, UUID.randomUUID(), doctor.getId());

        assertEquals(user.getId(), actorUserId);
        verify(patientProfileRepository, never()).findByUserId(user.getId());
    }

    @Test
    void requireAppointmentPartyDeniesDoctorOnAnotherClinicianSlot() {
        when(doctorProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(doctor));

        assertThrows(AccessDeniedException.class,
                () -> resolver.requireAppointmentParty(EMAIL, UUID.randomUUID(), UUID.randomUUID()));
        verify(patientProfileRepository, never()).findByUserId(user.getId());
    }
}
