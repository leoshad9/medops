/**
 * Preflight checks for the doctor report upload form.
 *
 * Mirrors the server-side gate in
 * medops-api/src/main/java/com/medops/files/domain/PdfUploadPolicy.java so an
 * obviously wrong file fails locally instead of after a multi-megabyte round
 * trip that ends in "400 INVALID_ARGUMENT". Messages reuse the API wording so
 * the client and server tell the user the same thing.
 */
export const MAX_PDF_BYTES = 10 * 1024 * 1024;

/** MIME types browsers report for real PDFs. */
const PDF_TYPES = new Set(["application/pdf", "application/x-pdf"]);

/** Types browsers fall back to when they cannot identify the picked file. */
const GENERIC_TYPES = new Set(["", "application/octet-stream", "binary/octet-stream"]);

const PDF_MAGIC = "%PDF-";

export function isPdfFileName(name: string): boolean {
  return name.trim().toLowerCase().endsWith(".pdf");
}

/**
 * Returns the message to show when the file cannot be uploaded, or null when it
 * passes every check that does not require reading the bytes.
 */
export function pdfFileProblem(file: File): string | null {
  if (file.size === 0) {
    return "A PDF file is required";
  }
  const type = (file.type || "").trim().toLowerCase();
  const namedPdf = isPdfFileName(file.name);
  // A concrete non-PDF type is trusted even when the name ends in .pdf, while an
  // unidentifiable type is allowed through for .pdf names and verified server-side.
  if (!PDF_TYPES.has(type) && !(namedPdf && GENERIC_TYPES.has(type))) {
    return "Only PDF files are accepted";
  }
  if (file.size > MAX_PDF_BYTES) {
    return "PDF files must be 10 MB or smaller";
  }
  return null;
}

/**
 * Reads only the first bytes of the file: a real PDF starts with "%PDF-".
 * This is the same magic-number gate the API applies before storing a report.
 */
export async function pdfHeaderProblem(file: File): Promise<string | null> {
  try {
    const header = new Uint8Array(await file.slice(0, PDF_MAGIC.length).arrayBuffer());
    const magic = String.fromCharCode(...header);
    return magic === PDF_MAGIC ? null : "Only valid PDF files are accepted";
  } catch {
    // A local read can fail for a file that moved on disk; let the API decide.
    return null;
  }
}

/**
 * Checks that the file has a plausible PDF end structure: a %EOF marker
 * preceded by either a trailer keyword (classic xref) or startxref
 * (xref stream). Mirrors the server-side gate in PdfUploadPolicy.
 * Reads only the tail bytes (max 4 KB) to stay lightweight.
 */
export async function pdfEndStructureProblem(file: File): Promise<string | null> {
  const TAIL_SIZE = 4096;
  try {
    const tail = new Uint8Array(
      await file.slice(Math.max(0, file.size - TAIL_SIZE)).arrayBuffer(),
    );
    const tailText = new TextDecoder().decode(tail);
    const eofIdx = tailText.lastIndexOf("%EOF");
    if (eofIdx < 0) {
      return "PDF is missing end-of-file marker";
    }
    const trailerIdx = tailText.lastIndexOf("trailer");
    const startxrefIdx = tailText.lastIndexOf("startxref");
    const structureIdx = Math.max(trailerIdx, startxrefIdx);
    if (structureIdx < 0 || structureIdx > eofIdx) {
      return "PDF is missing end-of-file marker";
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Guarantees the multipart part carries the MIME type the API requires. Some
 * browsers send an empty or generic type for .pdf files; the bytes are still
 * magic-checked and validated server-side, so this only fixes the header.
 */
export function asPdfUploadFile(file: File): File {
  const type = (file.type || "").trim().toLowerCase();
  if (PDF_TYPES.has(type)) {
    return file;
  }
  return new File([file], file.name, { type: "application/pdf" });
}
