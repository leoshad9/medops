package com.medops.prescriptions.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.appointments.application.AppointmentActorResolver;
import com.medops.clinical.ClinicalAccessService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.prescriptions.api.dto.PrescriptionResponse;
import com.medops.prescriptions.infrastructure.Prescription;
import com.medops.prescriptions.infrastructure.PrescriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrescriptionQueryService {

    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAccessService clinicalAccess;
    private final AppointmentActorResolver actorResolver;
    private final PrescriptionAssembler assembler;

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> list(String email, UUID patientId) {
        if (actorResolver.findDoctor(email).isPresent()) {
            return listForDoctor(email, patientId);
        }
        return listForPatient(email);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listForPatient(String patientEmail) {
        PatientProfile patient = actorResolver.requirePatient(patientEmail);
        return prescriptionRepository.findByPatientProfileIdOrderByCreatedAtDesc(patient.getId()).stream()
                .map(assembler::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listForDoctor(String doctorEmail, UUID patientId) {
        DoctorProfile doctor = actorResolver.requireDoctor(doctorEmail);
        List<Prescription> items = patientId == null
                ? prescriptionRepository.findByDoctorProfileIdOrderByCreatedAtDesc(doctor.getId())
                : listForTreatingDoctor(doctorEmail, doctor.getId(), patientId);
        return items.stream().map(assembler::toResponse).toList();
    }

    private List<Prescription> listForTreatingDoctor(String doctorEmail, UUID doctorId, UUID patientId) {
        clinicalAccess.requireTreatingDoctor(doctorEmail, patientId);
        return prescriptionRepository.findByDoctorProfileIdAndPatientProfileIdOrderByCreatedAtDesc(
                doctorId, patientId);
    }
}
