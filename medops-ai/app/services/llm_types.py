"""Shared LLM client types and errors (provider-agnostic)."""

from __future__ import annotations

from dataclasses import dataclass


class LlmError(Exception):
    """Base failure talking to the LLM provider."""


class LlmTimeoutError(LlmError):
    pass


class LlmUnavailableError(LlmError):
    pass


class LlmRateLimitError(LlmUnavailableError):
    """Provider rate limit / quota exhausted (HTTP 429)."""


class LlmResponseError(LlmError):
    pass


@dataclass(frozen=True)
class ChatResult:
    content: str
    prompt_tokens: int | None
    completion_tokens: int | None
    total_tokens: int | None
    latency_ms: int
