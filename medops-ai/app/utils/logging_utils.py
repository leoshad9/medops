"""Logging helpers for redacting secrets from logs."""

from __future__ import annotations

from typing import Dict


def mask_secret(value: str) -> str:
    if not value:
        return "(none)"
    s = value.strip()
    if len(s) <= 8:
        return "****"
    return s[:4] + "..." + s[-4:]


def redact_headers(headers: Dict[str, str]) -> Dict[str, str]:
    safe = {}
    for k, v in (headers or {}).items():
        lk = k.lower()
        if lk in ("authorization", "x-goog-api-key", "api-key", "x-api-key"):
            safe[k] = mask_secret(v)
        else:
            safe[k] = v
    return safe


def jittered_backoff(base: float, attempt: int, max_cap: float) -> float:
    """Compute exponential backoff with full jitter and cap.

    - base: base backoff seconds
    - attempt: 1-based attempt number
    - max_cap: maximum cap for backoff
    """
    import random

    exp = base * (2 ** (attempt - 1))
    cap = min(exp, max_cap)
    return random.uniform(0, cap)
