from fastapi.testclient import TestClient
from main import app
import time

client = TestClient(app)

def test_activate_and_validate():
    key = "TEST-KEY-123"
    # ensure deactivate
    client.post(f"/deactivate?key={key}")
    r = client.get(f"/validate?key={key}")
    assert r.status_code == 200
    assert r.json()["premium"] is False

    # activate for 1 day
    r = client.post(f"/activate?key={key}&days=1")
    assert r.status_code == 200
    data = r.json()
    assert data.get("ok") is True
    assert data.get("expires_at") is not None

    # validate now
    r = client.get(f"/validate?key={key}")
    assert r.status_code == 200
    assert r.json()["premium"] is True

    # deactivate
    client.post(f"/deactivate?key={key}")
    r = client.get(f"/validate?key={key}")
    assert r.json()["premium"] is False
