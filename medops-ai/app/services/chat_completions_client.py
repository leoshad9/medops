"""HTTP client for chat/completions-style LLM APIs."""

from __future__ import annotations

import asyncio
import logging
import time

import httpx

from app.config import settings
from app.services.llm_types import (
    ChatResult,
    LlmError,
    LlmRateLimitError,
    LlmResponseError,
    LlmTimeoutError,
    LlmUnavailableError,
)

logger = logging.getLogger(__name__)

_RETRYABLE_STATUS = {429, 500, 502, 503, 504}


class ChatCompletionsClient:
    """Adapter for POST /chat/completions."""

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

        payload = {
            "model": model,
            "temperature": settings.llm_temperature if temperature is None else temperature,
            "max_tokens": settings.llm_max_tokens if max_tokens is None else max_tokens,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        url = base.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {settings.llm_api_key.strip()}",
            "Content-Type": "application/json",
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
                await asyncio.sleep(_backoff_seconds(attempt, rate_limited=False))
                continue
            break
        if response.status_code in _RETRYABLE_STATUS:
            last_error = _retryable_error(response, attempt, attempts, latency_ms)
            provider_code, _ = _provider_error(response)
            if provider_code in {"credit_balance_exhausted", "insufficient_quota", "billing_not_active"}:
                break
            if attempt < attempts:
                await asyncio.sleep(
                    _backoff_seconds(attempt, rate_limited=response.status_code == 429)
                )
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


def _retryable_error(response: httpx.Response, attempt: int, attempts: int, latency_ms: int) -> LlmError:
    provider_code, provider_message = _provider_error(response)
    logger.warning(
        "llm_retryable_status status=%s attempt=%s/%s latency_ms=%s code=%s",
        response.status_code, attempt, attempts, latency_ms, provider_code,
    )
    if response.status_code == 429:
        return LlmRateLimitError(
            provider_message or "LLM provider rate-limited the request (HTTP 429). Try again shortly."
        )
    return LlmUnavailableError(f"LLM provider returned {response.status_code}")


def _handle_error_status(response: httpx.Response, latency_ms: int) -> None:
    if response.status_code == 401:
        raise LlmUnavailableError("LLM API key rejected")
    if response.status_code >= 400:
        provider_code, provider_message = _provider_error(response)
        logger.warning(
            "llm_client_error status=%s latency_ms=%s code=%s",
            response.status_code, latency_ms, provider_code,
        )
        raise LlmResponseError(provider_message or f"LLM provider returned {response.status_code}")


def _build_result(response: httpx.Response, latency_ms: int, *, model: str) -> ChatResult:
    data = response.json()
    content = _extract_content(data)
    usage = data.get("usage") or {}
    result = ChatResult(
        content=content,
        prompt_tokens=_as_int(usage.get("prompt_tokens")),
        completion_tokens=_as_int(usage.get("completion_tokens")),
        total_tokens=_as_int(usage.get("total_tokens")),
        latency_ms=latency_ms,
    )
    logger.info(
        "llm_ok dialect=chat_completions model=%s latency_ms=%s prompt_tokens=%s "
        "completion_tokens=%s total_tokens=%s",
        model, result.latency_ms, result.prompt_tokens,
        result.completion_tokens, result.total_tokens,
    )
    return result


def _backoff_seconds(attempt: int, *, rate_limited: bool) -> float:
    base = settings.llm_retry_backoff_seconds * attempt
    return max(base, 2.0 * attempt) if rate_limited else base


def _provider_error(response: httpx.Response) -> tuple[str | None, str | None]:
    try:
        payload = response.json()
        err = payload.get("error") if isinstance(payload, dict) else None
        if not isinstance(err, dict):
            return None, None
        code = err.get("code") or err.get("type")
        message = err.get("message")
        code_s = str(code) if code is not None else None
        message_s = str(message).strip() if isinstance(message, str) and message.strip() else None
        return code_s, message_s
    except Exception:  # noqa: BLE001
        return None, None


def _extract_content(data: dict) -> str:
    try:
        choices = data["choices"]
        message = choices[0]["message"]
        content = message.get("content")
    except (KeyError, IndexError, TypeError) as exc:
        raise LlmResponseError("Malformed LLM response") from exc
    if not isinstance(content, str) or not content.strip():
        raise LlmResponseError("Empty LLM content")
    return content


def _as_int(value: object) -> int | None:
    if isinstance(value, int):
        return value
    return None
