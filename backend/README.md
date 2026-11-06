# KIVYA Backend

FastAPI service for KIVYA's online AI mode. Wake-word detection and offline inference stay on the Android device; the backend is not in the microphone/audio path.

## Current API

- `GET /health`: public liveness/configuration summary. Does not expose secrets.
- `POST /v1/chat`: authenticated Gemini chat endpoint.
- Request: `{"message":"नमस्ते","language":"hi","history":[]}`
- Header: `Authorization: Bearer <KIVYA_API_TOKEN>`

Supported languages: `hi`, `en`, `auto`. Message length is capped at 6,000 characters and the optional context list at 12 entries. Server output tokens are clamped to 64–1,200. The API never returns provider exception text or credentials.

## Configuration

Set secrets in the hosting platform's secret manager, not in Git or the Android APK:

- `GEMINI_API_KEY`: Google AI Studio API key.
- `KIVYA_API_TOKEN`: random server access token, at least 32 characters.
- `GEMINI_MODEL`: optional model override; current default is `gemini-3.8-flash`.
- `GEMINI_TIMEOUT_SECONDS`: optional request timeout (5–90 seconds).
- `MAX_OUTPUT_TOKENS`: optional response cap (64–1,200).
- `CORS_ORIGINS`: optional comma-separated trusted browser origins.

If `KIVYA_API_TOKEN` is absent or shorter than 32 characters, chat requests fail closed with HTTP 503. Missing/invalid bearer tokens receive HTTP 401. Use HTTPS only.

**Important deployment note:** a shared bearer token embedded in a public APK can be extracted. This initial gate is suitable for server-side testing and controlled integration, not a final multi-user identity system. Before public release, replace it with per-user/device credentials (short-lived access tokens, refresh/revocation, and server-side authorization). Do not ship the shared token in the app.

## Cost and privacy safeguards

- Google model and Search tool quotas, availability, and billing vary by project. Verify them in Google AI Studio before deployment.
- A quota/provider rate-limit response is mapped to HTTP 429; the service does not silently switch to a paid provider.
- Gemini API data handling depends on the account's tier and terms. Do not send passwords, private messages, or sensitive personal data.
- AWS hosting can incur separate charges. Do not create billable AWS resources without the account owner's approval.
- The backend does not execute phone actions, access contacts, initiate calls, or control the device.

## Local development

```bash
cd backend
python -m venv .venv
# Activate the environment, then:
pip install -r requirements.txt
export GEMINI_API_KEY="..."
export KIVYA_API_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
uvicorn main:app --host 127.0.0.1 --port 8000
```

Health: `curl http://127.0.0.1:8000/health`

Chat:
```bash
curl -X POST http://127.0.0.1:8000/v1/chat \
  -H "Authorization: Bearer $KIVYA_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"नमस्ते","language":"hi","history":[]}'
```

Run tests from the repository root:

```bash
pytest -q backend/tests
```

## Architecture boundary

- **Android device:** `Hey KIVYA` wake-word, microphone capture, foreground-service notification, Single Command and Continuous Conversation modes, and offline local model inference.
- **Backend:** authenticated online chat, provider integration, quotas/rate limits, and (next phase) durable user-scoped conversation storage.
- Wake-word audio is not uploaded to this API.
