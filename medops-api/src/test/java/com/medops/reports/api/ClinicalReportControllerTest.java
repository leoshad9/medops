package com.medops.reports.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medops.auth.security.AuthRateLimitFilter;
import com.medops.auth.security.JwtAuthenticationFilter;
import com.medops.idempotency.application.IdempotencyExecutor;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ReportQueryService;
import com.medops.reports.application.SummarizeReportService;
import com.medops.reports.application.UploadReportService;
import com.medops.reports.domain.ReportStatus;

@WebMvcTest(
        controllers = ClinicalReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, AuthRateLimitFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
class ClinicalReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadReportService uploadReportService;
    @MockitoBean
    private ReportQueryService reportQueryService;
    @MockitoBean
    private SummarizeReportService summarizeReportService;
    @MockitoBean
    private IdempotencyExecutor idempotencyExecutor;

    @BeforeEach
    void passthroughIdempotency() {
        when(idempotencyExecutor.execute(any(), any(), any(), any(), eq(ClinicalReportResponse.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ClinicalReportResponse> action = invocation.getArgument(5);
                    return action.get();
                });
    }

    @Test
    void uploadReturns201WithEnvelope() throws Exception {
        UUID patientId = UUID.randomUUID();
        ClinicalReportResponse body = new ClinicalReportResponse(
                UUID.randomUUID(), patientId, UUID.randomUUID(), "Pat", "MRN-1", "Dr. Test",
                "CBC", null, ReportStatus.NEW, "cbc.pdf", 12, true, null, null, null, null);
        when(uploadReportService.upload(eq("doctor.test@medops.dev"), eq(patientId), eq("CBC"), eq(null), any()))
                .thenReturn(body);

        MockMultipartFile file = new MockMultipartFile(
                "file", "cbc.pdf", "application/pdf", "%PDF-1.4 body".getBytes());

        mockMvc.perform(multipart("/api/v1/patients/" + patientId + "/reports")
                        .file(file)
                        .param("title", "CBC")
                        .header("Idempotency-Key", "upload-1")
                        .principal(doctorAuth())
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    @Test
    void summarizeReturns200WithSummary() throws Exception {
        UUID reportId = UUID.randomUUID();
        ClinicalReportResponse body = new ClinicalReportResponse(
                reportId, UUID.randomUUID(), UUID.randomUUID(), "Pat", "MRN-1", "Dr. Test",
                "CBC", null, ReportStatus.NEW, "cbc.pdf", 12, true, null, null,
                "Plain overview", Instant.parse("2026-08-31T12:00:00Z"));
        when(summarizeReportService.summarizeOnDemand(eq(reportId), eq("patient@medops.dev")))
                .thenReturn(body);

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/summarize")
                        .principal(patientAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary").value("Plain overview"));
    }

    private static UsernamePasswordAuthenticationToken doctorAuth() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "doctor.test@medops.dev",
                "n/a",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_DOCTOR")));
    }

    private static UsernamePasswordAuthenticationToken patientAuth() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "patient@medops.dev",
                "n/a",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    }
}
