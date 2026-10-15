# -*- coding: utf-8 -*-
"""
Titan Bridge – runs inside Chaquopy on Android.
Loads the all-in-one TITAN script and starts the Flask/Waitress server.
"""
from __future__ import annotations

import os
import sys
import threading
import time
import traceback

os.environ.setdefault("TITAN_HOME", "/storage/emulated/0/MMM")
os.environ.setdefault("TITAN_HOST", "127.0.0.1")
os.environ.setdefault("TITAN_PORT", "8080")
os.environ.setdefault("TITAN_OPEN_BROWSER", "0")
os.environ.setdefault("TITAN_DEBUG", "0")
os.environ.setdefault("TITAN_LIVE_TRADING", "0")
os.environ.setdefault("TITAN_ANALYSIS_ONLY", "1")

_server_thread: threading.Thread | None = None
_stop_event = threading.Event()


def _prepare_storage(fallback_files_dir: str | None = None) -> None:
    """Create the MMM folder tree that TITAN expects."""
    candidates = [
        "/storage/emulated/0/MMM",
        "/sdcard/MMM",
        "/storage/self/primary/MMM",
    ]
    if fallback_files_dir:
        candidates.append(os.path.join(fallback_files_dir, "MMM"))

    root = None
    for path in candidates:
        try:
            os.makedirs(path, exist_ok=True)
            probe = os.path.join(path, ".titan_probe")
            with open(probe, "w", encoding="utf-8") as f:
                f.write("ok")
            os.remove(probe)
            root = path
            break
        except Exception:
            continue

    if root is None:
        if fallback_files_dir:
            root = os.path.join(fallback_files_dir, "MMM")
            os.makedirs(root, exist_ok=True)
        else:
            raise RuntimeError(
                "Cannot create MMM storage folder. "
                "Grant All-files access permission."
            )

    os.environ["TITAN_HOME"] = root
    for sub in ("data", "cache", "memory", "secrets",
                "cache/tmp", "cache/pycache", "cache/history",
                "data/replay", "data/flask_instance"):
        os.makedirs(os.path.join(root, sub), exist_ok=True)

    print(f"[titan_bridge] Storage root = {root}", flush=True)


def start_server(host: str = "127.0.0.1", port: int = 8080,
                 fallback_files_dir: str | None = None) -> None:
    global _server_thread

    if _server_thread is not None and _server_thread.is_alive():
        print("[titan_bridge] Server already running", flush=True)
        return

    _prepare_storage(fallback_files_dir)

    try:
        import titan_pro_all as titan
    except Exception as exc:
        print(f"[titan_bridge] Failed to import titan_pro_all: {exc}", flush=True)
        traceback.print_exc()
        raise

    try:
        ns = titan.load(entry_file=__file__, as_main=False)
    except Exception as exc:
        print(f"[titan_bridge] load() failed: {exc}", flush=True)
        traceback.print_exc()
        raise

    run_titan = ns.get("run_titan")
    if run_titan is None:
        raise RuntimeError("run_titan not found after loading layers")

    os.environ["TITAN_HOST"] = str(host)
    os.environ["TITAN_PORT"] = str(port)

    print(f"[titan_bridge] Starting TITAN on {host}:{port} …", flush=True)

    try:
        run_titan(open_browser=False)
    except Exception as exc:
        print(f"[titan_bridge] run_titan crashed: {exc}", flush=True)
        traceback.print_exc()
        raise


def stop_server() -> None:
    _stop_event.set()
    print("[titan_bridge] stop_server requested", flush=True)


def health() -> dict:
    return {
        "ok": True,
        "running": _server_thread is not None and _server_thread.is_alive(),
        "storage": os.environ.get("TITAN_HOME", ""),
    }
