"""HTTP client for generateContent-style LLM APIs."""

from __future__ import annotations

import asyncio
import logging
import time

import httpx

from app.config import settings
from app.services.llm_types import (
    ChatResult,
    LlmRateLimitError,
    LlmResponseError,
    LlmTimeoutError,
    LlmUnavailableError,
)

logger = logging.getLogger(__name__)

_RETRYABLE_STATUS = {429, 500, 502, 503, 504}


class GenerateContentClient:
    """Adapter for POST /models/{model}:generateContent."""

    async def chat(
        self,
        *,
        system: str,
        user: str,
        temperature: float | None = None,
        max_tokens: int | None = None,
    ) -> ChatResult:
        if not settings.llm_api_key.strip():
            raise LlmUnavailableError("LLM_API_KEY is not configured")

        model = settings.active_model
        base = settings.active_base_url
        if not model or not base:
            raise LlmUnavailableError("LLM model/base URL is not configured")

        url = base.rstrip("/") + f"/models/{model}:generateContent"
        headers = {
            "Content-Type": "application/json",
            "x-goog-api-key": settings.llm_api_key.strip(),
        }
        payload = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": user}]}],
            "generationConfig": {
                "temperature": settings.llm_temperature if temperature is None else temperature,
                "maxOutputTokens": settings.llm_max_tokens if max_tokens is None else max_tokens,
            },
        }
        return await _run_with_retry(url, headers, payload, model=model)


async def _run_with_retry(url: str, headers: dict, payload: dict, *, model: str) -> ChatResult:
    last_error: Exception | None = None
    attempts = max(1, settings.llm_max_attempts)
    for attempt in range(1, attempts + 1):
        response, latency_ms, last_error = await _attempt_request(
            url, headers, payload, attempt, attempts, model=model
        )
        if response is None:
            if attempt < attempts:
                await asyncio.sleep(_backoff_seconds(attempt))
                continue
            break
        if response.status_code in _RETRYABLE_STATUS:
            last_error = _retryable_error(response, attempt, attempts, latency_ms)
            if response.status_code != 429 and attempt < attempts:
                await asyncio.sleep(_backoff_seconds(attempt))
                continue
            break
        _handle_error_status(response, latency_ms)
        return _build_result(response, latency_ms, model=model)
    assert last_error is not None
    raise last_error


async def _attempt_request(
    url: str,
    headers: dict,
    payload: dict,
    attempt: int,
    attempts: int,
    *,
    model: str,
) -> tuple[httpx.Response | None, int, Exception | None]:
    started = time.perf_counter()
    try:
        timeout = httpx.Timeout(settings.request_timeout_seconds)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(url, headers=headers, json=payload)
        latency_ms = int((time.perf_counter() - started) * 1000)
        return response, latency_ms, None
    except httpx.TimeoutException:
        logger.warning("llm_timeout attempt=%s/%s model=%s", attempt, attempts, model)
        return None, 0, LlmTimeoutError("LLM request timed out")
    except httpx.HTTPError:
        logger.warning("llm_network_error attempt=%s/%s model=%s", attempt, attempts, model)
        return None, 0, LlmUnavailableError("LLM network error")


def _retryable_error(response: httpx.Response, attempt: int, attempts: int, latency_ms: int):
    code, message = _provider_error(response)
    logger.warning(
        "llm_retryable_status status=%s attempt=%s/%s latency_ms=%s code=%s message=%s",
        response.status_code, attempt, attempts, latency_ms, code, message,
    )
    if response.status_code == 429:
        return LlmRateLimitError(
            message or "LLM provider rate-limited the request (HTTP 429). Try again shortly."
        )
    return LlmUnavailableError(f"LLM provider returned {response.status_code}")


def _handle_error_status(response: httpx.Response, latency_ms: int) -> None:
    if response.status_code in {401, 403}:
        raise LlmUnavailableError("LLM API key rejected")
    if response.status_code >= 400:
        code, message = _provider_error(response)
        logger.warning(
            "llm_client_error status=%s latency_ms=%s code=%s message=%s",
            response.status_code, latency_ms, code, message,
        )
        raise LlmResponseError(message or f"LLM provider returned {response.status_code}")


def _build_result(response: httpx.Response, latency_ms: int, *, model: str) -> ChatResult:
    data = response.json()
    content = _extract_content(data)
    usage = data.get("usageMetadata") or {}
    result = ChatResult(
        content=content,
        prompt_tokens=_as_int(usage.get("promptTokenCount")),
        completion_tokens=_as_int(usage.get("candidatesTokenCount")),
        total_tokens=_as_int(usage.get("totalTokenCount")),
        latency_ms=latency_ms,
    )
    logger.info(
        "llm_ok dialect=generate_content model=%s latency_ms=%s prompt_tokens=%s "
        "completion_tokens=%s total_tokens=%s finish_reason=%s",
        model, result.latency_ms, result.prompt_tokens,
        result.completion_tokens, result.total_tokens, _finish_reason(data),
    )
    return result


def _backoff_seconds(attempt: int) -> float:
    return settings.llm_retry_backoff_seconds * attempt


def _provider_error(response: httpx.Response) -> tuple[str | None, str | None]:
    try:
        payload = response.json()
        err = payload.get("error") if isinstance(payload, dict) else None
        if not isinstance(err, dict):
            return None, None
        code = err.get("status") or err.get("code")
        message = err.get("message")
        code_s = str(code) if code is not None else None
        message_s = str(message).strip() if isinstance(message, str) and message.strip() else None
        return code_s, message_s
    except Exception:  # noqa: BLE001
        return None, None


def _finish_reason(data: dict) -> str | None:
    try:
        return data["candidates"][0].get("finishReason")
    except (KeyError, IndexError, TypeError):
        return None


def _extract_content(data: dict) -> str:
    try:
        candidates = data["candidates"]
        parts = candidates[0]["content"]["parts"]
        texts = [p.get("text", "") for p in parts if isinstance(p, dict)]
        content = "".join(texts)
    except (KeyError, IndexError, TypeError) as exc:
        raise LlmResponseError("Malformed LLM response") from exc
    if not isinstance(content, str) or not content.strip():
        raise LlmResponseError("Empty LLM content")
    return content


def _as_int(value: object) -> int | None:
    if isinstance(value, int):
        return value
    return None
