import { describe, expect, it } from "vitest";

import {
  MAX_PDF_BYTES,
  asPdfUploadFile,
  isPdfFileName,
  pdfEndStructureProblem,
  pdfFileProblem,
  pdfHeaderProblem,
} from "./pdfUpload";

const PDF_BYTES = "%PDF-1.4\n1 0 obj\nendobj\ntrailer\n<< /Root 1 0 R >>\nstartxref\n9\n%%EOF\n";

function pdfFile(name = "cbc.pdf", type = "application/pdf", body: string = PDF_BYTES): File {
  return new File([body], name, { type });
}

function withSize(file: File, size: number): File {
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("isPdfFileName", () => {
  it("matches the .pdf extension case-insensitively", () => {
    expect(isPdfFileName("CBC.PDF")).toBe(true);
    expect(isPdfFileName(" scan .pdf")).toBe(true);
    expect(isPdfFileName("cbc.png")).toBe(false);
  });
});

describe("pdfFileProblem", () => {
  it("accepts a PDF reported with the PDF content type", () => {
    expect(pdfFileProblem(pdfFile())).toBeNull();
  });

  it("accepts a .pdf whose type the browser could not identify", () => {
    expect(pdfFileProblem(pdfFile("cbc.pdf", ""))).toBeNull();
    expect(pdfFileProblem(pdfFile("cbc.pdf", "application/octet-stream"))).toBeNull();
  });

  it("rejects an empty file with the API wording", () => {
    expect(pdfFileProblem(withSize(pdfFile(), 0))).toBe("A PDF file is required");
  });

  it("rejects other document and image types", () => {
    expect(pdfFileProblem(pdfFile("notes.txt", "text/plain"))).toBe("Only PDF files are accepted");
    expect(pdfFileProblem(pdfFile("scan.png", "image/png"))).toBe("Only PDF files are accepted");
    expect(pdfFileProblem(pdfFile("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
      .toBe("Only PDF files are accepted");
  });

  it("rejects a renamed non-PDF file the browser identified by content", () => {
    expect(pdfFileProblem(pdfFile("report.pdf", "image/png"))).toBe("Only PDF files are accepted");
  });

  it("rejects files above the API limit", () => {
    expect(pdfFileProblem(withSize(pdfFile(), MAX_PDF_BYTES + 1))).toBe("PDF files must be 10 MB or smaller");
  });

  it("accepts a file exactly at the API limit", () => {
    expect(pdfFileProblem(withSize(pdfFile(), MAX_PDF_BYTES))).toBeNull();
  });
});

describe("pdfHeaderProblem", () => {
  it("accepts bytes that start with the PDF magic number", async () => {
    await expect(pdfHeaderProblem(pdfFile())).resolves.toBeNull();
  });

  it("rejects a mislabelled file that only claims to be a PDF", async () => {
    await expect(pdfHeaderProblem(pdfFile("report.pdf", "application/pdf", "just some text")))
      .resolves.toBe("Only valid PDF files are accepted");
  });
});

describe("pdfEndStructureProblem", () => {
  it("accepts a valid PDF with trailer and %EOF", async () => {
    await expect(pdfEndStructureProblem(pdfFile())).resolves.toBeNull();
  });

  it("accepts a valid PDF with startxref and %EOF", async () => {
    const pdf = new File(
      ["%PDF-1.5\n1 0 obj\nendobj\nstartxref\n420\n%%EOF\n"],
      "test.pdf",
      { type: "application/pdf" },
    );
    await expect(pdfEndStructureProblem(pdf)).resolves.toBeNull();
  });

  it("rejects a truncated PDF with no %EOF", async () => {
    const pdf = new File(["%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>"], "test.pdf", {
      type: "application/pdf",
    });
    await expect(pdfEndStructureProblem(pdf)).resolves.toBe("PDF is missing end-of-file marker");
  });

  it("rejects a PDF whose final structure marker follows the last %EOF", async () => {
    // Server rule: the structure marker must precede the final %EOF. A file that
    // keeps writing trailer data after its closing marker is not a readable PDF.
    const pdf = new File(
      ["%PDF-1.4\ntrailer\n<< /Root 1 0 R >>\nstartxref\n9\n%%EOF\ntrailer\n"],
      "test.pdf",
      { type: "application/pdf" },
    );
    await expect(pdfEndStructureProblem(pdf)).resolves.toBe("PDF is missing end-of-file marker");
  });

  it("accepts an incrementally updated PDF with several %EOF sections", async () => {
    const pdf = new File(
      [
        "%PDF-1.4\n1 0 obj\nendobj\ntrailer\n<< /Root 1 0 R >>\nstartxref\n9\n%%EOF\n",
        "2 0 obj\nendobj\ntrailer\n<< /Root 1 0 R /Size 3 >>\nstartxref\n120\n%%EOF\n",
      ],
      "test.pdf",
      { type: "application/pdf" },
    );
    await expect(pdfEndStructureProblem(pdf)).resolves.toBeNull();
  });
});

describe("asPdfUploadFile", () => {
  it("keeps files the browser already typed as PDF", () => {
    const file = pdfFile();
    expect(asPdfUploadFile(file)).toBe(file);
  });

  it("re-labels a .pdf with a missing or generic type for the multipart part", () => {
    const upload = asPdfUploadFile(pdfFile("cbc.pdf", ""));
    expect(upload.type).toBe("application/pdf");
    expect(upload.name).toBe("cbc.pdf");
    expect(upload.size).toBe(pdfFile().size);
  });
});
