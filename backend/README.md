# KIVYA Backend

FastAPI backend for KIVYA online AI. Wake-word detection, microphone capture, and offline inference remain on Android; wake audio is never sent to this API.

## Guest mode

- `POST /auth/guest` creates an anonymous guest and returns a random bearer credential.
- Only a SHA-256 hash of the credential is stored. The raw credential is returned once.
- Credentials expire after `GUEST_SESSION_DAYS` (default 30, max 90) and can be revoked with `POST /auth/logout`.
- `DELETE /auth/guest` deletes/deactivates that guest's server-side data and revokes sessions.
- No email, password, or Google account is required.

Keep the bearer credential in Android Keystore-backed encrypted storage. Do not log it or put it in URLs. Guest identity is not an account recovery mechanism: clearing app data or losing the credential can make cloud history inaccessible.

## Chat and history

All `/v1/*` conversation endpoints require `Authorization: Bearer <guest access token>`.
- `POST /v1/chat`: `{"message":"नमस्ते","language":"hi","conversation_id":null}`; returns reply and conversation ID. Messages are stored server-side.
- `GET /v1/conversations`
- `POST /v1/conversations` with optional title
- `GET /v1/conversations/{id}/messages`
- `DELETE /v1/conversations/{id}`

Every conversation lookup is scoped to the authenticated guest. The client cannot choose another guest's identity. Guest history can remain local-only in the Android app; cloud persistence is optional from the product UX.

## Security firewall

- Opaque high-entropy bearer credentials; only hashes stored at rest.
- Session expiry, logout revocation, guest deletion, ownership checks.
- Input length/type validation, bounded model output, generic provider errors.
- Per-process IP/session rate limiting. This is a basic abuse guard, **not** a distributed production firewall. Use a managed WAF and shared Redis-backed rate limiter before scaling horizontally.
- HTTPS required in deployment; configure trusted proxy IP handling at the hosting layer.
- Restrict CORS to exact trusted origins if browser clients are enabled.

## Configuration

Set `GEMINI_API_KEY` and `DATABASE_URL` in the hosting secret manager. SQLite is for local development; use managed PostgreSQL for production. Never commit API keys or embed server secrets in the APK. Review Gemini data handling and billing for the selected Google account/project before enabling online chat.

## Local development

```bash
cd backend
python -m venv .venv
pip install -r requirements.txt
export GEMINI_API_KEY="..."
uvicorn main:app --host 127.0.0.1 --port 8000
```

Run tests from repository root: `pytest -q backend/tests`.
