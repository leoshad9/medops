package com.medops.reports.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.files.domain.ClinicalFileStorage;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.infrastructure.ClinicalReport;
import com.medops.reports.infrastructure.ClinicalReportRepository;
import com.medops.shared.audit.AuditEventType;
import com.medops.shared.audit.AuditService;
import com.medops.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private final ClinicalReportRepository reportRepository;
    private final ClinicalFileStorage fileStorage;
    private final ClinicalAccessService clinicalAccess;
    private final AppointmentActorResolver actorResolver;
    private final ClinicalReportAssembler assembler;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ClinicalReportResponse> list(String email, UUID patientId) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return listForDoctor(email, patientId);
        }
        return listForPatient(email);
    }

    @Transactional(readOnly = true)
    public List<ClinicalReportResponse> listForPatient(String patientEmail) {
        PatientProfile patient = actorResolver.requirePatient(patientEmail);
        return reportRepository.findByPatientProfileIdOrderByCreatedAtDesc(patient.getId()).stream()
                .map(assembler::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClinicalReportResponse> listForDoctor(String doctorEmail, UUID patientId) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        List<ClinicalReport> reports = patientId == null
                ? reportRepository.findByDoctorProfileIdOrderByCreatedAtDesc(doctor.getId())
                : listForTreatingDoctor(doctorEmail, doctor.getId(), patientId);
        return reports.stream().map(assembler::toResponse).toList();
    }

    @Transactional
    public ClinicalReportResponse markReviewed(UUID reportId, String patientEmail) {
        ClinicalReport report = requireReport(reportId);
        clinicalAccess.assertPatientOwns(report.getPatientProfileId(), patientEmail);
        report.markReviewed(clock.instant());
        PatientProfile patient = actorResolver.requirePatient(patientEmail);
        auditService.recordEvent(AuditEventType.REPORT_REVIEWED, patient.getUserId(), patientEmail);
        return assembler.toResponse(report);
    }

    @Transactional(readOnly = true)
    public Resource download(UUID reportId, String email) {
        ClinicalReport report = requireReport(reportId);
        UUID actorUserId = clinicalAccess.requireReportReader(email, report.getPatientProfileId());
        auditService.recordEvent(AuditEventType.REPORT_VIEWED, actorUserId, email);
        return fileStorage.load(report.getStorageKey());
    }

    public ClinicalReport requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
    }

    private List<ClinicalReport> listForTreatingDoctor(String doctorEmail, UUID doctorId, UUID patientId) {
        clinicalAccess.requireTreatingDoctor(doctorEmail, patientId);
        return reportRepository.findByDoctorProfileIdAndPatientProfileIdOrderByCreatedAtDesc(doctorId, patientId);
    }
}
