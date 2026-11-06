import hashlib
import os
import secrets
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone
from typing import Literal
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from google import genai
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, select, delete
from sqlalchemy.orm import Session, sessionmaker

from database import Base, Guest, GuestSession, Conversation, Message

APP_NAME = "Kivya API"
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.8-flash").strip()
MAX_OUTPUT_TOKENS = min(max(int(os.getenv("MAX_OUTPUT_TOKENS", "600")), 64), 1200)
GEMINI_TIMEOUT_SECONDS = min(max(float(os.getenv("GEMINI_TIMEOUT_SECONDS", "45")), 5), 90)
RATE_LIMIT_PER_MINUTE = min(max(int(os.getenv("RATE_LIMIT_PER_MINUTE", "30")), 5), 120)
SESSION_DAYS = min(max(int(os.getenv("GUEST_SESSION_DAYS", "30")), 1), 90)

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./kivya.db")
_engine_options = {"pool_pre_ping": True}
if DATABASE_URL.startswith("sqlite"):
    _engine_options["connect_args"] = {"check_same_thread": False}
engine = create_engine(DATABASE_URL, **_engine_options)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
Base.metadata.create_all(bind=engine)

app = FastAPI(title=APP_NAME, version="0.5.0")
_bearer = HTTPBearer(auto_error=False)
_RATE_LOCK = threading.Lock()
_REQUESTS: dict[str, deque[float]] = defaultdict(deque)

allowed_origins = [o.strip() for o in os.getenv("CORS_ORIGINS", "").split(",") if o.strip()]
if allowed_origins:
    app.add_middleware(CORSMiddleware, allow_origins=allowed_origins,
                       allow_credentials=False, allow_methods=["GET", "POST", "DELETE"],
                       allow_headers=["Authorization", "Content-Type"])


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def enforce_rate_limit(client_ip: str) -> None:
    now = time.monotonic()
    with _RATE_LOCK:
        bucket = _REQUESTS[client_ip]
        while bucket and now - bucket[0] >= 60:
            bucket.popleft()
        if len(bucket) >= RATE_LIMIT_PER_MINUTE:
            raise HTTPException(429, "KIVYA request limit reached. Try again later.")
        bucket.append(now)


def current_guest(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> Guest:
    raw = credentials.credentials if credentials else ""
    if not raw or len(raw) < 40:
        raise HTTPException(401, "Guest session required", headers={"WWW-Authenticate": "Bearer"})
    token_hash = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    session = db.scalar(select(GuestSession).where(GuestSession.token_hash == token_hash))
    now = datetime.now(timezone.utc)
    if not session or session.revoked or session.expires_at.replace(tzinfo=timezone.utc) <= now:
        raise HTTPException(401, "Guest session is invalid or expired")
    guest = db.get(Guest, session.guest_id)
    if not guest or guest.deleted:
        raise HTTPException(401, "Guest session is no longer available")
    session.last_seen_at = now
    db.commit()
    return guest


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=6000)
    language: Literal["hi", "en", "auto"] = "auto"
    conversation_id: UUID | None = None


class ChatResponse(BaseModel):
    reply: str
    model: str
    conversation_id: UUID


class GuestSessionResponse(BaseModel):
    guest_id: UUID
    access_token: str
    token_type: str = "bearer"
    expires_at: datetime


class ConversationCreate(BaseModel):
    title: str = Field(default="New conversation", min_length=1, max_length=120)


def _owned_conversation(db: Session, guest_id, conversation_id):
    item = db.scalar(select(Conversation).where(
        Conversation.id == conversation_id,
        Conversation.guest_id == guest_id,
        Conversation.deleted.is_(False),
    ))
    if not item:
        raise HTTPException(404, "Conversation not found")
    return item


@app.get("/health")
def health():
    return {"status": "ok", "service": "kivya-api", "ai_provider": "google-gemini-interactions",
            "model": GEMINI_MODEL, "google_search": True, "version": "0.5.0"}


@app.post("/auth/guest", response_model=GuestSessionResponse)
def create_guest_session(request: Request, db: Session = Depends(get_db)):
    ip = request.client.host if request.client else "unknown"
    enforce_rate_limit("guest:" + ip)
    guest = Guest()
    raw_token = secrets.token_urlsafe(48)
    expires = datetime.now(timezone.utc) + timedelta(days=SESSION_DAYS)
    db.add(guest)
    db.flush()
    db.add(GuestSession(guest_id=guest.id,
                        token_hash=hashlib.sha256(raw_token.encode("utf-8")).hexdigest(),
                        expires_at=expires))
    db.commit()
    return GuestSessionResponse(guest_id=guest.id, access_token=raw_token, expires_at=expires)


@app.post("/auth/logout")
def logout(guest: Guest = Depends(current_guest), credentials: HTTPAuthorizationCredentials = Depends(_bearer),
           db: Session = Depends(get_db)):
    digest = hashlib.sha256(credentials.credentials.encode("utf-8")).hexdigest()
    session = db.scalar(select(GuestSession).where(GuestSession.token_hash == digest,
                                                   GuestSession.guest_id == guest.id))
    if session:
        session.revoked = True
        db.commit()
    return {"status": "logged_out"}


@app.get("/v1/conversations")
def list_conversations(guest: Guest = Depends(current_guest), db: Session = Depends(get_db)):
    rows = db.scalars(select(Conversation).where(
        Conversation.guest_id == guest.id, Conversation.deleted.is_(False)
    ).order_by(Conversation.updated_at.desc()).limit(100)).all()
    return [{"id": str(c.id), "title": c.title, "created_at": c.created_at,
             "updated_at": c.updated_at} for c in rows]


@app.post("/v1/conversations")
def create_conversation(payload: ConversationCreate, guest: Guest = Depends(current_guest),
                        db: Session = Depends(get_db)):
    item = Conversation(guest_id=guest.id, title=payload.title)
    db.add(item)
    db.commit()
    db.refresh(item)
    return {"id": str(item.id), "title": item.title, "created_at": item.created_at}


@app.get("/v1/conversations/{conversation_id}/messages")
def get_messages(conversation_id: UUID, guest: Guest = Depends(current_guest),
                 db: Session = Depends(get_db)):
    _owned_conversation(db, guest.id, conversation_id)
    rows = db.scalars(select(Message).where(
        Message.conversation_id == conversation_id
    ).order_by(Message.created_at.asc()).limit(500)).all()
    return [{"id": str(m.id), "role": m.role, "content": m.content, "created_at": m.created_at}
            for m in rows]


@app.delete("/v1/conversations/{conversation_id}")
def delete_conversation(conversation_id: UUID, guest: Guest = Depends(current_guest),
                        db: Session = Depends(get_db)):
    item = _owned_conversation(db, guest.id, conversation_id)
    item.deleted = True
    db.commit()
    return {"status": "deleted"}


@app.delete("/auth/guest")
def delete_guest_data(guest: Guest = Depends(current_guest), db: Session = Depends(get_db)):
    guest.deleted = True
    db.query(GuestSession).filter(GuestSession.guest_id == guest.id).update({"revoked": True})
    db.commit()
    return {"status": "guest_data_deleted"}


@app.post("/v1/chat", response_model=ChatResponse)
def chat(payload: ChatRequest, request: Request, guest: Guest = Depends(current_guest),
         db: Session = Depends(get_db)):
    ip = request.client.host if request.client else "unknown"
    enforce_rate_limit("chat:" + str(guest.id) + ":" + ip)
    if not GEMINI_API_KEY:
        raise HTTPException(503, "Gemini API is not configured")

    if payload.conversation_id:
        conversation = _owned_conversation(db, guest.id, payload.conversation_id)
    else:
        conversation = Conversation(guest_id=guest.id, title=payload.message[:80])
        db.add(conversation)
        db.flush()

    recent = db.scalars(select(Message).where(
        Message.conversation_id == conversation.id
    ).order_by(Message.created_at.desc()).limit(12)).all()
    context = "\n".join(f"{m.role}: {m.content[:1000]}" for m in reversed(recent))
    language_hint = {"hi": "Reply in natural, clear Hindi.",
                     "en": "Reply in natural, clear English.",
                     "auto": "Reply in the language used by the user."}[payload.language]
    instruction = ("You are KIVYA, a helpful, honest phone and technology assistant. "
                   "Be concise, practical, and friendly. Use Google Search when current facts "
                   "materially improve the answer. Never claim to control the phone unless the "
                   "Android app performed the action. " + language_hint)
    try:
        client = genai.Client(api_key=GEMINI_API_KEY)
        interaction = client.interactions.create(
            model=GEMINI_MODEL,
            input=("Recent conversation context:\n" + context +
                   "\n\nCurrent user message:\n" + payload.message),
            system_instruction=instruction,
            tools=[{"type": "google_search"}],
            generation_config={"max_output_tokens": MAX_OUTPUT_TOKENS, "thinking_level": "medium"},
            timeout=GEMINI_TIMEOUT_SECONDS,
        )
        reply = (interaction.output_text or "").strip()
        if not reply:
            raise HTTPException(502, "Gemini returned no text. Please try again.")
        db.add(Message(conversation_id=conversation.id, role="user", content=payload.message))
        db.add(Message(conversation_id=conversation.id, role="assistant", content=reply))
        conversation.updated_at = datetime.now(timezone.utc)
        db.commit()
        return ChatResponse(reply=reply, model=GEMINI_MODEL, conversation_id=conversation.id)
    except HTTPException:
        db.rollback()
        raise
    except Exception as exc:
        db.rollback()
        name = type(exc).__name__.lower()
        if "ratelimit" in name or "resourceexhausted" in name:
            raise HTTPException(429, "Gemini quota reached or service is busy.")
        if "auth" in name or "permission" in name:
            raise HTTPException(503, "Gemini API key or project access is not configured correctly.")
        raise HTTPException(502, "Gemini AI is temporarily unavailable. Please try again.")
