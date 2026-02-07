from fastapi import FastAPI, Query
from pydantic import BaseModel
import json
from pathlib import Path
from datetime import datetime, timedelta

app = FastAPI()

# License storage on disk (demo). In production use a secure DB and auth.
DATA_FILE = Path(__file__).parent / "licenses.json"
if DATA_FILE.exists():
    try:
        with DATA_FILE.open() as f:
            licenses = json.load(f)
    except Exception:
        licenses = {}
else:
    licenses = {}


class ValidateResponse(BaseModel):
    premium: bool
    expires_at: str | None = None


def save_licenses():
    try:
        with DATA_FILE.open("w") as f:
            json.dump(licenses, f)
    except Exception:
        pass


@app.get("/validate", response_model=ValidateResponse)
def validate(key: str = Query(...)):
    entry = licenses.get(key)
    if not entry:
        return {"premium": False, "expires_at": None}
    # entry can be timestamp string
    try:
        expires = datetime.fromisoformat(entry)
        is_premium = expires > datetime.utcnow()
        return {"premium": is_premium, "expires_at": entry}
    except Exception:
        return {"premium": False, "expires_at": None}


@app.post("/activate")
def activate(key: str = Query(...), days: int = Query(30)):
    # Activate subscription for `days` days
    expires = (datetime.utcnow() + timedelta(days=days)).isoformat()
    licenses[key] = expires
    save_licenses()
    return {"ok": True, "expires_at": expires}


@app.post("/deactivate")
def deactivate(key: str = Query(...)):
    if key in licenses:
        del licenses[key]
        save_licenses()
    return {"ok": True}
