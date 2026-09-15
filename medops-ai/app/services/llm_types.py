"""Shared LLM client types and errors (provider-agnostic)."""

from __future__ import annotations

from dataclasses import dataclass


class LlmError(Exception):
    """Base failure talking to the LLM provider."""


class LlmTimeoutError(LlmError):
    """LLM request exceeded the configured timeout."""


class LlmUnavailableError(LlmError):
    """LLM provider is unreachable or credentials are invalid."""


class LlmRateLimitError(LlmUnavailableError):
    """Provider rate limit / quota exhausted (HTTP 429)."""


class LlmResponseError(LlmError):
    """LLM returned a response that could not be parsed or validated."""


@dataclass(frozen=True)
class ChatResult:
    """Normalised chat response returned by all provider adapters."""

    content: str
    prompt_tokens: int | None
    completion_tokens: int | None
    total_tokens: int | None
    latency_ms: int
