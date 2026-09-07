package com.medops.notification.infrastructure.kafka;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes after-commit domain events and turns them into user notifications.
 * Recipients are enriched by joining the aggregate id to the owning profiles'
 * {@code userId}s (patients and doctors). Deduplication happens in
 * {@link NotificationService} keyed on {@code (user_id, source_event_id)}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "medops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final AppointmentRepository appointmentRepository;
    private final ClinicalReportRepository clinicalReportRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    @KafkaListener(
            topics = "${medops.messaging.appointments-topic}",
            groupId = "medops-api-notifications",
            containerFactory = "domainEventKafkaListenerContainerFactory")
    public void onAppointmentBooked(DomainEventMessage message) {
        if (message == null || !"AppointmentBooked".equals(message.eventType()) || message.appointmentId() == null) {
            return;
        }
        log.info("Consuming AppointmentBooked for notifications appointmentId={}", message.appointmentId());
        appointmentRepository.findById(message.appointmentId())
                .ifPresent(appointment -> notifyAppointmentParties(appointment, message.eventId()));
    }

    @KafkaListener(
            topics = "${medops.messaging.reports-topic}",
            groupId = "medops-api-notifications",
            containerFactory = "domainEventKafkaListenerContainerFactory")
    public void onReportUploaded(DomainEventMessage message) {
        if (message == null || !"ReportUploaded".equals(message.eventType()) || message.reportId() == null) {
            return;
        }
        log.info("Consuming ReportUploaded for notifications reportId={}", message.reportId());
        clinicalReportRepository.findById(message.reportId())
                .ifPresent(report -> notifyReportParties(report, message.eventId()));
    }

    private void notifyAppointmentParties(Appointment appointment, UUID sourceEventId) {
        DoctorProfile doctor = doctorProfileRepository.findById(appointment.getDoctorProfileId()).orElse(null);
        PatientProfile patient = patientProfileRepository.findById(appointment.getPatientProfileId()).orElse(null);
        if (doctor == null || patient == null) {
            log.warn("Cannot enrich AppointmentBooked appointmentId={}", appointment.getId());
            return;
        }
        String startsAt = appointment.getStartsAt().toString();
        notificationService.create(new CreateNotification(
                patient.getUserId(), NotificationType.APPOINTMENT_BOOKED,
                "Appointment confirmed",
                "Your appointment with " + doctor.getFullName() + " is confirmed at " + startsAt + ".",
                "APPOINTMENT", appointment.getId(), sourceEventId));
        notificationService.create(new CreateNotification(
                doctor.getUserId(), NotificationType.APPOINTMENT_BOOKED,
                "New appointment",
                "Appointment with " + patient.getFullName() + " at " + startsAt + ".",
                "APPOINTMENT", appointment.getId(), sourceEventId));
    }

    private void notifyReportParties(ClinicalReport report, UUID sourceEventId) {
        DoctorProfile doctor = doctorProfileRepository.findById(report.getDoctorProfileId()).orElse(null);
        PatientProfile patient = patientProfileRepository.findById(report.getPatientProfileId()).orElse(null);
        if (doctor == null || patient == null) {
            log.warn("Cannot enrich ReportUploaded reportId={}", report.getId());
            return;
        }
        notificationService.create(new CreateNotification(
                patient.getUserId(), NotificationType.REPORT_UPLOADED,
                "New report available",
                doctor.getFullName() + " uploaded \"" + report.getTitle() + "\" for you.",
                "REPORT", report.getId(), sourceEventId));
        notificationService.create(new CreateNotification(
                doctor.getUserId(), NotificationType.REPORT_UPLOADED,
                "Report uploaded",
                "Report \"" + report.getTitle() + "\" for " + patient.getFullName() + " was uploaded.",
                "REPORT", report.getId(), sourceEventId));
    }
}
