"""Generate a simple text PDF and POST it to the medops-ai summarizer endpoint."""
from __future__ import annotations

import base64
import json
import os
from pathlib import Path

from reportlab.pdfgen import canvas
import httpx


def make_pdf(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(path))
    textobject = c.beginText(40, 800)
    textobject.setFont("Helvetica", 12)
    for line in text.splitlines():
        textobject.textLine(line)
    c.drawText(textobject)
    c.showPage()
    c.save()


def main():
    pdf_path = Path("tmp/test_text.pdf")
    sample_text = (
        "MedOps AI Summarizer Test\n"
        "This PDF contains several sentences about a fictional patient case.\n"
        "Patient: John Doe. Age: 45. Symptoms: fever, cough, shortness of breath.\n"
        "Assessment: Suspected respiratory infection. Plan: chest X-ray, blood tests, and supportive care.\n"
    )
    make_pdf(pdf_path, sample_text)

    b = pdf_path.read_bytes()
    b64 = base64.b64encode(b).decode("ascii")

    url = os.getenv("MEDOPS_AI_URL", "http://127.0.0.1:8001/ai/reports/local-test/summary")
    payload = {"content_base64": b64}
    print("Posting to", url)
    with httpx.Client(timeout=60.0) as client:
        r = client.post(url, json=payload)
    print("Status:", r.status_code)
    try:
        print(json.dumps(r.json(), indent=2))
    except Exception:
        print(r.text)


if __name__ == "__main__":
    main()
