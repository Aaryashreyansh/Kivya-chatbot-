import hmac
import os
from typing import Literal

from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from google import genai
from pydantic import BaseModel, Field

APP_NAME = "Kivya API"
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.8-flash").strip()
KIVYA_API_TOKEN = os.getenv("KIVYA_API_TOKEN", "")
MAX_OUTPUT_TOKENS = min(max(int(os.getenv("MAX_OUTPUT_TOKENS", "1200")), 64), 4096)
GEMINI_TIMEOUT_SECONDS = min(max(float(os.getenv("GEMINI_TIMEOUT_SECONDS", "45")), 5), 90)

app = FastAPI(title=APP_NAME, version="0.3.0")

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
    return {
        "status": "ok",
        "service": "kivya-api",
        "ai_provider": "google-gemini-interactions",
        "model": GEMINI_MODEL,
        "google_search": True,
    }


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
        "Be concise, practical, and friendly. Use Google Search when current "
        "facts, recent events, changing information, or source verification would "
        "materially improve the answer. Distinguish verified facts from uncertainty. "
        "Do not claim to control the phone unless the Android app has actually "
        "performed the action. "
        + language_hint
    )

    try:
        client = genai.Client(api_key=GEMINI_API_KEY)
        interaction = client.interactions.create(
            model=GEMINI_MODEL,
            input=payload.message,
            system_instruction=system_instruction,
            tools=[{"type": "google_search"}],
            generation_config={
                "max_output_tokens": MAX_OUTPUT_TOKENS,
                "thinking_level": "medium",
            },
            # The SDK request timeout is bounded by the hosting platform as well.
            timeout=GEMINI_TIMEOUT_SECONDS,
        )
        reply = (interaction.output_text or "").strip()
        if not reply:
            raise HTTPException(
                status_code=502,
                detail="Gemini returned no text. Please try again.",
            )
        return ChatResponse(reply=reply, model=GEMINI_MODEL)
    except HTTPException:
        raise
    except Exception as exc:
        # Do not expose provider responses, request headers, or credentials to clients.
        error_name = type(exc).__name__.lower()
        if "ratelimit" in error_name or "resourceexhausted" in error_name:
            raise HTTPException(
                status_code=429,
                detail="Gemini quota reached or service is busy. Try again later.",
            )
        if "auth" in error_name or "permission" in error_name:
            raise HTTPException(
                status_code=503,
                detail="Gemini API key or project access is not configured correctly.",
            )
        raise HTTPException(
            status_code=502,
            detail="Gemini AI is temporarily unavailable. Please try again.",
        )
