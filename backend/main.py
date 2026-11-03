import hmac
import os
from typing import Literal

import httpx
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

APP_NAME = "Kivya API"
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip()
KIVYA_API_TOKEN = os.getenv("KIVYA_API_TOKEN", "")
MAX_OUTPUT_TOKENS = min(max(int(os.getenv("MAX_OUTPUT_TOKENS", "600")), 64), 1200)
GEMINI_TIMEOUT_SECONDS = min(max(float(os.getenv("GEMINI_TIMEOUT_SECONDS", "25")), 5), 60)

app = FastAPI(title=APP_NAME, version="0.2.0")

allowed_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "").split(",")
    if origin.strip()
]
if allowed_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed_origins,
        allow_credentials=False,
        allow_methods=["POST", "GET"],
        allow_headers=["Authorization", "Content-Type"],
    )


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=6000)
    language: Literal["hi", "en", "auto"] = "auto"


class ChatResponse(BaseModel):
    reply: str
    model: str


def require_auth(authorization: str | None) -> None:
    if not KIVYA_API_TOKEN:
        raise HTTPException(status_code=503, detail="API authentication is not configured")
    expected = f"Bearer {KIVYA_API_TOKEN}"
    if not authorization or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


@app.get("/health")
def health():
    return {"status": "ok", "service": "kivya-api", "ai_provider": "google-gemini"}


@app.post("/v1/chat", response_model=ChatResponse)
def chat(payload: ChatRequest, authorization: str | None = Header(default=None)):
    require_auth(authorization)

    if not GEMINI_API_KEY:
        raise HTTPException(status_code=503, detail="Gemini API is not configured")

    language_hint = {
        "hi": "Reply in natural, clear Hindi.",
        "en": "Reply in natural, clear English.",
        "auto": "Reply in the language used by the user.",
    }[payload.language]

    system_instruction = (
        "You are KIVYA, a helpful, honest phone and technology assistant. "
        "Be concise, practical, and friendly. Do not claim to control the phone "
        "unless the Android app has actually performed the action. "
        + language_hint
    )
    url = (
        "https://generativelanguage.googleapis.com/v1beta/"
        f"models/{GEMINI_MODEL}:generateContent"
    )
    body = {
        "systemInstruction": {"parts": [{"text": system_instruction}]},
        "contents": [{"role": "user", "parts": [{"text": payload.message}]}],
        "generationConfig": {
            "maxOutputTokens": MAX_OUTPUT_TOKENS,
            "temperature": 0.4,
            "topP": 0.9,
        },
    }

    try:
        with httpx.Client(timeout=GEMINI_TIMEOUT_SECONDS) as client:
            response = client.post(
                url,
                headers={
                    "x-goog-api-key": GEMINI_API_KEY,
                    "Content-Type": "application/json",
                },
                json=body,
            )
        if response.status_code == 429:
            raise HTTPException(
                status_code=429,
                detail="Gemini free-tier limit reached or service is busy. Try again later.",
            )
        if response.status_code in (401, 403):
            raise HTTPException(
                status_code=503,
                detail="Gemini API key or project access is not configured correctly.",
            )
        if response.status_code >= 500:
            raise HTTPException(
                status_code=502,
                detail="Gemini AI is temporarily unavailable. Try again later.",
            )
        if response.is_error:
            raise HTTPException(status_code=502, detail="Gemini could not process this request.")

        data = response.json()
        candidates = data.get("candidates", [])
        parts = candidates[0].get("content", {}).get("parts", []) if candidates else []
        reply = "\n".join(
            part["text"] for part in parts if isinstance(part.get("text"), str)
        ).strip()
        if not reply:
            raise HTTPException(
                status_code=502,
                detail="Gemini returned no text. Please try again.",
            )
        return ChatResponse(reply=reply, model=GEMINI_MODEL)
    except HTTPException:
        raise
    except (httpx.TimeoutException, httpx.RequestError, ValueError):
        raise HTTPException(
            status_code=502,
            detail="Gemini AI is temporarily unreachable. Please try again.",
        )
