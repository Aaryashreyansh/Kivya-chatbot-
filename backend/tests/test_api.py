from fastapi.testclient import TestClient

import main

client = TestClient(main.app)


def guest_token():
    response = client.post("/auth/guest")
    assert response.status_code == 200
    return response.json()["access_token"]


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def test_health_is_public_and_does_not_expose_credentials():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "api_key" not in body
    assert "token" not in body


def test_guest_session_creation_and_authenticated_access():
    token = guest_token()
    response = client.get("/v1/conversations", headers=auth(token))
    assert response.status_code == 200
    assert response.json() == []


def test_private_conversations_are_scoped_to_guest():
    token_a = guest_token()
    token_b = guest_token()
    created = client.post("/v1/conversations", headers=auth(token_a),
                          json={"title": "Private chat"})
    assert created.status_code == 200
    visible_to_a = client.get("/v1/conversations", headers=auth(token_a)).json()
    visible_to_b = client.get("/v1/conversations", headers=auth(token_b)).json()
    assert len(visible_to_a) == 1
    assert visible_to_b == []


def test_missing_and_invalid_guest_credentials_are_rejected():
    assert client.get("/v1/conversations").status_code == 401
    assert client.get("/v1/conversations", headers=auth("invalid")).status_code == 401


def test_logout_revokes_guest_session():
    token = guest_token()
    response = client.post("/auth/logout", headers=auth(token))
    assert response.status_code == 200
    assert client.get("/v1/conversations", headers=auth(token)).status_code == 401


def test_guest_can_delete_own_data_and_session():
    token = guest_token()
    response = client.delete("/auth/guest", headers=auth(token))
    assert response.status_code == 200
    assert client.get("/v1/conversations", headers=auth(token)).status_code == 401


def test_message_length_is_validated():
    token = guest_token()
    response = client.post("/v1/chat", headers=auth(token),
                            json={"message": "a" * 6001})
    assert response.status_code == 422
