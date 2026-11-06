import os

from fastapi.testclient import TestClient

import main


client = TestClient(main.app)


def test_health_is_public_and_does_not_expose_credentials():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "api_key" not in body
    assert "token" not in body


def test_chat_rejects_missing_bearer_token(monkeypatch):
    monkeypatch.setenv("KIVYA_API_TOKEN", "x" * 48)
    response = client.post("/v1/chat", json={"message": "hello"})
    assert response.status_code == 401


def test_chat_fails_closed_if_server_token_is_not_configured(monkeypatch):
    monkeypatch.delenv("KIVYA_API_TOKEN", raising=False)
    response = client.post(
        "/v1/chat",
        headers={"Authorization": "Bearer " + "x" * 48},
        json={"message": "hello"},
    )
    assert response.status_code == 503


def test_chat_rejects_invalid_bearer_token(monkeypatch):
    monkeypatch.setenv("KIVYA_API_TOKEN", "a" * 48)
    response = client.post(
        "/v1/chat",
        headers={"Authorization": "Bearer " + "b" * 48},
        json={"message": "hello"},
    )
    assert response.status_code == 401


def test_chat_validates_message_length(monkeypatch):
    monkeypatch.setenv("KIVYA_API_TOKEN", "x" * 48)
    response = client.post(
        "/v1/chat",
        headers={"Authorization": "Bearer " + "x" * 48},
        json={"message": "a" * 6001},
    )
    assert response.status_code == 422
