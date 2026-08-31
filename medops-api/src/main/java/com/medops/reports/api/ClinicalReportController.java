package com.medops.reports.api;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medops.files.application.MultipartPdfs;
import com.medops.files.domain.UploadedPdf;
import com.medops.idempotency.application.IdempotencyExecutor;
import com.medops.reports.api.dto.ClinicalReportResponse;
import com.medops.reports.application.ReportQueryService;
import com.medops.reports.application.SummarizeReportService;
import com.medops.reports.application.UploadReportService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
public class ClinicalReportController {

    private final UploadReportService uploadReportService;
    private final ReportQueryService reportQueryService;
    private final SummarizeReportService summarizeReportService;
    private final IdempotencyExecutor idempotencyExecutor;

    @PostMapping(path = "/api/v1/patients/{patientId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalReportResponse>> upload(
            Authentication authentication,
            @PathVariable UUID patientId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam @NotBlank @Size(max = 200) String title,
            @RequestParam(required = false) @Size(max = 1000) String notes,
            @RequestParam("file") MultipartFile file) {
        UploadedPdf pdf = MultipartPdfs.required(file);
        String requestHash = IdempotencyExecutor.sha256(
                patientId.toString(),
                title,
                notes,
                IdempotencyExecutor.sha256(pdf.content()));
        ClinicalReportResponse response = idempotencyExecutor.execute(
                authentication.getName(),
                "reports.upload",
                idempotencyKey,
                requestHash,
                ClinicalReportResponse.class,
                () -> uploadReportService.upload(authentication.getName(), patientId, title, notes, pdf));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Report uploaded"));
    }

    @GetMapping("/api/v1/reports")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<List<ClinicalReportResponse>>> list(
            Authentication authentication,
            @RequestParam(required = false) UUID patientId) {
        List<ClinicalReportResponse> items = reportQueryService.list(authentication.getName(), patientId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping("/api/v1/reports/{reportId}/review")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<ClinicalReportResponse>> review(
            Authentication authentication,
            @PathVariable UUID reportId) {
        return ResponseEntity.ok(ApiResponse.success(
                reportQueryService.markReviewed(reportId, authentication.getName()),
                "Report marked as reviewed"));
    }

    @PostMapping("/api/v1/reports/{reportId}/summarize")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<ClinicalReportResponse>> summarize(
            Authentication authentication,
            @PathVariable UUID reportId) {
        ClinicalReportResponse response = summarizeReportService.summarizeOnDemand(
                reportId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Report summarized"));
    }

    @GetMapping("/api/v1/reports/{reportId}/file")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<Resource> download(Authentication authentication, @PathVariable UUID reportId) {
        Resource file = reportQueryService.download(reportId, authentication.getName());
        String filename = reportQueryService.requireReport(reportId).getOriginalFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }
}
