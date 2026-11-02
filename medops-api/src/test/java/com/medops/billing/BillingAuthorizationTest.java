package com.medops.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.auth.security.principal.MedOpsUser;
import com.medops.auth.infrastructure.repository.UserRepository;
import com.medops.billing.infrastructure.Invoice;
import com.medops.billing.infrastructure.InvoiceItem;
import com.medops.billing.infrastructure.InvoiceRepository;
import com.medops.billing.infrastructure.Payment;
import com.medops.billing.infrastructure.PaymentRepository;
import com.medops.billing.domain.InvoiceStatus;
import com.medops.patients.domain.Gender;
import com.medops.patients.application.PatientRegistrationService;
import com.medops.patients.infrastructure.PatientProfile;
import com.medops.patients.infrastructure.PatientProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BillingAuthorizationTest {

    private static final long TOTAL_CENTS = 5000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PatientRegistrationService patientRegistrationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PatientProfileRepository patientProfileRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void listPayments_allowsOwningPatient() throws Exception {
        String email = "billing-owner-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile owner = registerPatient(email);
        Invoice invoice = saveInvoice(owner.getId(), InvoiceStatus.ISSUED);
        recordPayment(invoice.getId());

        mockMvc.perform(get("/api/v1/patients/{patientId}/billing/invoices/{invoiceId}/payments",
                        owner.getId(), invoice.getId())
                        .with(user(patientPrincipal(email, owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listPayments_rejectsStrangerPatient() throws Exception {
        String ownerEmail = "billing-owner-stranger-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile owner = registerPatient(ownerEmail);
        String strangerEmail = "billing-stranger-" + UUID.randomUUID() + "@medops.dev";
        PatientProfile stranger = registerPatient(strangerEmail);
        Invoice invoice = saveInvoice(owner.getId(), InvoiceStatus.ISSUED);

        mockMvc.perform(get("/api/v1/patients/{patientId}/billing/invoices/{invoiceId}/payments",
                        stranger.getId(), invoice.getId())
                        .with(user(patientPrincipal(strangerEmail, stranger))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(400))
                .andExpect(jsonPath("$.error.message").value("Invoice does not belong to this patient"));
    }

    // --- fixtures ---

    protected PatientProfile registerPatient(String email) {
        patientRegistrationService.registerPatient(new com.medops.patients.api.dto.RegisterPatientRequest(
                email, "Password123!", "Billing IT", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "+12345678901"));
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return patientProfileRepository.findByUserId(userId).orElseThrow();
    }

    protected Invoice saveInvoice(UUID patientProfileId, InvoiceStatus status) {
        Invoice invoice = Invoice.builder()
                .patientProfileId(patientProfileId)
                .status(status)
                .items(new java.util.ArrayList<>())
                .build();
        InvoiceItem item = InvoiceItem.builder()
                .description("Consultation")
                .quantity(1)
                .unitPriceCents(TOTAL_CENTS)
                .invoice(invoice)
                .build();
        invoice.setItems(List.of(item));
        return invoiceRepository.saveAndFlush(invoice);
    }

    protected Payment recordPayment(UUID invoiceId) {
        Payment payment = Payment.builder()
                .invoice(invoiceRepository.findById(invoiceId).orElseThrow())
                .idempotencyKey("auth-pay-" + UUID.randomUUID())
                .amountCents(TOTAL_CENTS)
                .method(com.medops.billing.domain.PaymentMethod.STRIPE)
                .status(com.medops.billing.domain.PaymentStatus.COMPLETED)
                .reference("auth-pay-" + UUID.randomUUID())
                .paidAt(java.time.Instant.now())
                .build();
        return paymentRepository.saveAndFlush(payment);
    }

    private static MedOpsUser patientPrincipal(String email, PatientProfile patient) {
        return new MedOpsUser(patient.getUserId(), email, "irrelevant", "PATIENT",
                List.of(new SimpleGrantedAuthority("ROLE_PATIENT")), true, true);
    }
}
