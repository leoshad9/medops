"""Simple in-memory metrics helper used for basic instrumentation in tests.

This is intentionally minimal: thread-safe counters with a small API to
`increment` and `get` counters. Not for production use; replace with real
telemetry in staging/production.
"""
from threading import Lock

_counters = {}
_lock = Lock()


def increment(name: str, amount: int = 1) -> None:
    with _lock:
        _counters[name] = _counters.get(name, 0) + amount


def get(name: str) -> int:
    with _lock:
        return _counters.get(name, 0)


def reset() -> None:
    with _lock:
        _counters.clear()
