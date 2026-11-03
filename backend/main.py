import hmac
import os
from typing import Literal

import boto3
from botocore.exceptions import BotoCoreError, ClientError
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

APP_NAME = "Kivya API"
AWS_REGION = os.getenv("AWS_REGION", "ap-south-1")
MODEL_ID = os.getenv("BEDROCK_MODEL_ID", "").strip()
API_TOKEN = os.getenv("KIVYA_API_TOKEN", "")
MAX_OUTPUT_TOKENS = min(max(int(os.getenv("MAX_OUTPUT_TOKENS", "600")), 64), 1200)

app = FastAPI(title=APP_NAME, version="0.1.0")

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
    if not API_TOKEN:
        raise HTTPException(status_code=503, detail="API authentication is not configured")
    expected = f"Bearer {API_TOKEN}"
    if not authorization or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")

@app.get("/health")
def health():
    return {"status": "ok", "service": "kivya-api"}

@app.post("/v1/chat", response_model=ChatResponse)
def chat(payload: ChatRequest, authorization: str | None = Header(default=None)):
    require_auth(authorization)

    if not MODEL_ID:
        raise HTTPException(status_code=503, detail="Bedrock model is not configured")

    language_hint = {
        "hi": "Reply in Hindi.",
        "en": "Reply in English.",
        "auto": "Reply in the language used by the user.",
    }[payload.language]

    try:
        client = boto3.client("bedrock-runtime", region_name=AWS_REGION)
        result = client.converse(
            modelId=MODEL_ID,
            system=[{"text": "You are Kivya, a helpful phone and technology assistant. " + language_hint}],
            messages=[{"role": "user", "content": [{"text": payload.message}]}],
            inferenceConfig={
                "maxTokens": MAX_OUTPUT_TOKENS,
                "temperature": 0.4,
                "topP": 0.9,
            },
        )
        blocks = result.get("output", {}).get("message", {}).get("content", [])
        reply = "\n".join(block["text"] for block in blocks if "text" in block).strip()
        if not reply:
            raise HTTPException(status_code=502, detail="The AI model returned an empty response")
        return ChatResponse(reply=reply, model=MODEL_ID)
    except HTTPException:
        raise
    except (ClientError, BotoCoreError):
        # Do not expose AWS error details, request data, or credentials to the client.
        raise HTTPException(status_code=502, detail="AI service is temporarily unavailable")
