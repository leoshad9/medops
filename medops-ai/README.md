MedOps AI service

Environment variables

- LLM_PROVIDER: generate_content or chat_completions
- LLM_API_KEY: API key or token
- LLM_MODEL: model name (e.g., gemini-3.6-flash)
- LLM_BASE_URL: base URL for the provider (e.g., https://generativelanguage.googleapis.com)
- LLM_USE_BEARER: when set to true, credentials are sent in `Authorization: Bearer <token>` instead of query param or `x-goog-api-key` header.

Run locally:

```bash
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```
