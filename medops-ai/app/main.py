"""MedOps AI Service — FastAPI.

Spring Boot owns clinical auth/business rules and calls this service over HTTP
for LLM inference. Keep PHI out of logs; validate model output before returning.

Route definitions live in ``app.routers``; this module only wires the app.
"""

from fastapi import FastAPI

from app.config import settings
from app.routers import health, reports

app = FastAPI(title=settings.app_name)
app.include_router(health.router)
app.include_router(reports.router)

