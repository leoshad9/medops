package com.medops.appointments.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medops.appointments.api.dto.BookAppointmentRequest;
import com.medops.appointments.domain.AppointmentStatus;
import com.medops.appointments.infrastructure.AppointmentRepository;
import com.medops.auth.repository.UserRepository;
import com.medops.doctors.api.dto.RegisterDoctorRequest;
import com.medops.doctors.application.DoctorRegistrationService;
import com.medops.doctors.infrastructure.DoctorProfile;
import com.medops.doctors.infrastructure.DoctorProfileRepository;
import com.medops.patients.api.dto.RegisterPatientRequest;
import com.medops.patients.application.PatientRegistrationService;
import com.medops.patients.domain.Gender;
import com.medops.shared.exception.ConflictException;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * Proves two concurrent bookings for the same doctor slot cannot both succeed —
 * the partial unique index plus {@code saveAndFlush} race mapping is the safety net.
 * <p>
 * Uses embedded PostgreSQL (Zonky) so the race is exercised without a Docker daemon.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
class AppointmentBookingConcurrencyIT {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PatientRegistrationService patientRegistrationService;
    @Autowired
    private DoctorRegistrationService doctorRegistrationService;
    @Autowired
    private DoctorProfileRepository doctorProfileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookAppointmentService bookAppointmentService;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void onlyOneBookingSucceedsWhenTwoPatientsRaceForSameSlot() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        doctorRegistrationService.registerDoctor(new RegisterDoctorRequest(
                "doc-" + suffix + "@medops.dev",
                "Password123!",
                "Dr Race",
                "Cardiology",
                "LIC-" + suffix,
                "+919876543210"));
        DoctorProfile doctor = doctorProfileRepository.findByUserId(
                        userRepository.findByEmail("doc-" + suffix + "@medops.dev").orElseThrow().getId())
                .orElseThrow();

        String patientA = "pat-a-" + suffix + "@medops.dev";
        String patientB = "pat-b-" + suffix + "@medops.dev";
        patientRegistrationService.registerPatient(new RegisterPatientRequest(
                patientA, "Password123!", "Patient A", LocalDate.of(1990, 1, 1), Gender.FEMALE, "+919811111111"));
        patientRegistrationService.registerPatient(new RegisterPatientRequest(
                patientB, "Password123!", "Patient B", LocalDate.of(1991, 2, 2), Gender.MALE, "+919822222222"));

        Instant startsAt = nextWeekdaySlot(LocalTime.of(10, 0));
        BookAppointmentRequest request = new BookAppointmentRequest(doctor.getId(), startsAt, "race");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String email : List.of(patientA, patientB)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!go.await(10, TimeUnit.SECONDS)) {
                            unexpected.incrementAndGet();
                            return;
                        }
                        bookAppointmentService.book(email, request);
                        successes.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpected.incrementAndGet();
                    } catch (ConflictException ex) {
                        conflicts.incrementAndGet();
                    } catch (Exception ex) {
                        unexpected.incrementAndGet();
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected.get()).as("unexpected failures").isZero();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(appointmentRepository.countByDoctorProfileIdAndStatusAndStartsAt(
                doctor.getId(), AppointmentStatus.BOOKED, startsAt)).isEqualTo(1);
    }

    private static Instant nextWeekdaySlot(LocalTime time) {
        LocalDate date = LocalDate.now(CLINIC_ZONE).plusWeeks(2);
        while (DayOfWeek.SUNDAY.equals(date.getDayOfWeek())) {
            date = date.plusDays(1);
        }
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }
}
