package com.medops.notification.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.Appointment;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.messaging.infrastructure.DomainEventMessage;
import com.medops.notification.application.CreateNotification;
import com.medops.notification.application.NotificationService;
import com.medops.notification.domain.NotificationType;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;
import com.medops.reports.domain.ReportStatus;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ClinicalReportRepository clinicalReportRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(notificationService, appointmentRepository, clinicalReportRepository,
                patientProfileRepository, doctorProfileRepository);
    }

    @Test
    void appointmentBookedNotifiesPatientAndDoctor() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientProfileId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .patientProfileId(patientProfileId)
                .doctorProfileId(doctorProfileId)
                .startsAt(Instant.parse("2026-08-31T04:30:00Z"))
                .endsAt(Instant.parse("2026-08-31T05:00:00Z"))
                .status(AppointmentStatus.BOOKED)
                .build();
        PatientProfile patient = PatientProfile.builder()
                .id(patientProfileId).userId(patientUserId).fullName("Ananya Rao").build();
        DoctorProfile doctor = DoctorProfile.builder()
                .id(doctorProfileId).userId(doctorUserId).fullName("Dr. Khan").build();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(patientProfileRepository.findById(patientProfileId)).thenReturn(Optional.of(patient));
        when(doctorProfileRepository.findById(doctorProfileId)).thenReturn(Optional.of(doctor));

        consumer.onAppointmentBooked(
                new DomainEventMessage("AppointmentBooked", eventId, appointmentId, null, Instant.now()));

        ArgumentCaptor<CreateNotification> captor = ArgumentCaptor.forClass(CreateNotification.class);
        verify(notificationService, times(2)).create(captor.capture());
        List<CreateNotification> created = captor.getAllValues();
        assertThat(created).extracting(CreateNotification::userId)
                .containsExactlyInAnyOrder(patientUserId, doctorUserId);
        assertThat(created).extracting(CreateNotification::title)
                .containsExactlyInAnyOrder("Appointment confirmed", "New appointment");
        assertThat(created).allMatch(n -> n.type() == NotificationType.APPOINTMENT_BOOKED);
        assertThat(created).allMatch(n -> n.referenceType().equals("APPOINTMENT"));
        assertThat(created).allMatch(n -> n.referenceId().equals(appointmentId));
        assertThat(created).allMatch(n -> n.sourceEventId().equals(eventId));
    }

    @Test
    void appointmentBookedIgnoresNonAppointmentEvents() {
        UUID eventId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();

        consumer.onAppointmentBooked(
                new DomainEventMessage("ReportUploaded", eventId, null, reportId, Instant.now()));

        verifyNoInteractions(appointmentRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void reportUploadedNotifiesPatientAndDoctor() {
        UUID reportId = UUID.randomUUID();
        UUID patientProfileId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ClinicalReport report = ClinicalReport.builder()
                .id(reportId)
                .patientProfileId(patientProfileId)
                .doctorProfileId(doctorProfileId)
                .title("Complete Blood Count")
                .status(ReportStatus.NEW)
                .storageKey("reports/cbc.pdf")
                .originalFilename("cbc.pdf")
                .contentType("application/pdf")
                .sizeBytes(1L)
                .build();
        PatientProfile patient = PatientProfile.builder()
                .id(patientProfileId).userId(patientUserId).fullName("Ananya Rao").build();
        DoctorProfile doctor = DoctorProfile.builder()
                .id(doctorProfileId).userId(doctorUserId).fullName("Dr. Khan").build();
        when(clinicalReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(patientProfileRepository.findById(patientProfileId)).thenReturn(Optional.of(patient));
        when(doctorProfileRepository.findById(doctorProfileId)).thenReturn(Optional.of(doctor));

        consumer.onReportUploaded(
                new DomainEventMessage("ReportUploaded", eventId, null, reportId, Instant.now()));

        ArgumentCaptor<CreateNotification> captor = ArgumentCaptor.forClass(CreateNotification.class);
        verify(notificationService, times(2)).create(captor.capture());
        List<CreateNotification> created = captor.getAllValues();
        assertThat(created).extracting(CreateNotification::title)
                .containsExactlyInAnyOrder("New report available", "Report uploaded");
        assertThat(created).allMatch(n -> n.type() == NotificationType.REPORT_UPLOADED);
        assertThat(created).allMatch(n -> n.referenceType().equals("REPORT"));
        assertThat(created).allMatch(n -> n.referenceId().equals(reportId));
        assertThat(created).allMatch(n -> n.sourceEventId().equals(eventId));
    }

    @Test
    void reportUploadedIgnoresNonReportEvents() {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        consumer.onReportUploaded(
                new DomainEventMessage("AppointmentBooked", eventId, appointmentId, null, Instant.now()));

        verifyNoInteractions(clinicalReportRepository);
        verifyNoInteractions(notificationService);
    }
}
