# Kivya AI backend (starter)

This is a small FastAPI service that sends chat requests to Amazon Bedrock in
`ap-south-1` by default. It is a scaffold, not a deployed service.

## Safety and deployment notes

- Never put AWS access keys, Bedrock credentials, or the API token in the Android APK.
- Run this service with an AWS IAM role (for example, a role attached to the
  eventual compute service). Do not create long-lived IAM user keys for the app.
- Restrict IAM to the selected Bedrock inference profile and the required
  foundation-model resources. Do not grant `bedrock:*`.
- Keep the service behind HTTPS. Set a strong `KIVYA_API_TOKEN` using the
  hosting provider's secret manager, not a committed file.
- Configure `CORS_ORIGINS` only for trusted web origins. Native Android is not
  governed by browser CORS.
- The chat endpoint has a 6,000-character input limit and a capped output-token
  setting. Model calls are billable. Do not deploy or enable inference until
  model access, current pricing, and an adult account owner's spending approval
  have been checked.
- No GitHub write, AWS deployment, shell, or other tool execution is exposed
  by this endpoint. Those capabilities need separate scoped permissions and
  explicit per-action confirmation.

## Configure and run locally

1. Create a virtual environment and install `requirements.txt`.
2. Copy `.env.example` to `.env` locally. Never commit `.env`.
3. Set `BEDROCK_MODEL_ID` to the exact verified model or inference-profile ID.
4. Set `KIVYA_API_TOKEN` to a long random secret.
5. Authenticate the local AWS SDK through an approved AWS role/profile.
6. Start with `uvicorn main:app --host 127.0.0.1 --port 8000` from this directory.

Health check: `GET /health`

Chat: `POST /v1/chat` with JSON `{"message":"नमस्ते","language":"hi"}`
and header `Authorization: Bearer <your-token>`.

The current app still needs an Android API client and a configured HTTPS backend
URL before the AI Tools mode can use this service.
