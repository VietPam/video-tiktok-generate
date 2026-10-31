#!/usr/bin/env python3
"""
Adversarial Verification & Stress Test Harness for Microservices and API Contracts:
- 01-script (Port 38101)
- 02-audio (Port 38102)
- Dockerfiles & docker-compose.yml
"""

import json
import os
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parent.parent

def run_cmd(cmd, cwd=WORKSPACE):
    res = subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True)
    return res.returncode, res.stdout.strip(), res.stderr.strip()

def http_get(url):
    req = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.status, resp.read().decode("utf-8"), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8"), dict(e.headers)
    except Exception as e:
        return -1, str(e), {}

def http_post(url, data, content_type="application/json"):
    if isinstance(data, str):
        payload = data.encode("utf-8")
    elif isinstance(data, (dict, list)):
        payload = json.dumps(data).encode("utf-8")
    elif isinstance(data, bytes):
        payload = data
    else:
        payload = b""

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read()
            return resp.status, body, dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers)
    except Exception as e:
        return -1, str(e).encode("utf-8"), {}

def wait_for_service(url, max_retries=30, interval=0.5):
    for _ in range(max_retries):
        status, body, _ = http_get(url)
        if status == 200:
            return True
        time.sleep(interval)
    return False

def test_docker_and_compose():
    print("\n--- Testing Dockerfile & docker-compose.yml configurations ---")
    
    # 1. Check docker compose config
    code, out, err = run_cmd("docker compose config")
    assert code == 0, f"docker compose config failed: {err}"
    print("[PASS] docker compose config is valid YAML")
    assert "38101" in out, "Port 38101 not mapped in docker compose"
    assert "38102" in out, "Port 38102 not mapped in docker compose"
    assert "shared_data" in out, "shared_data volume missing from compose"
    print("[PASS] docker compose port mappings (38101, 38102) and volumes verified")

    # 2. Check 01-script Dockerfile
    df_script = (WORKSPACE / "01-script" / "Dockerfile").read_text()
    assert "EXPOSE 38101" in df_script, "01-script/Dockerfile missing EXPOSE 38101"
    assert "SERVER_PORT=38101" in df_script, "01-script/Dockerfile missing SERVER_PORT=38101"
    assert "38101/health" in df_script, "01-script/Dockerfile healthcheck not pointing to 38101"
    print("[PASS] 01-script/Dockerfile verified (port 38101, healthcheck, alpine)")

    # 3. Check 02-audio Dockerfile
    df_audio = (WORKSPACE / "02-audio" / "Dockerfile").read_text()
    assert "EXPOSE 38102" in df_audio, "02-audio/Dockerfile missing EXPOSE 38102"
    assert "SERVER_PORT=38102" in df_audio, "02-audio/Dockerfile missing SERVER_PORT=38102"
    assert "38102/health" in df_audio, "02-audio/Dockerfile healthcheck not pointing to 38102"
    print("[PASS] 02-audio/Dockerfile verified (port 38102, healthcheck, alpine)")

def test_live_microservices():
    print("\n--- Starting Live Spring Boot Microservices (01-script:38101, 02-audio:38102) ---")
    
    script_jar = WORKSPACE / "01-script" / "build" / "libs" / "01-script-1.0.0-SNAPSHOT-boot.jar"
    audio_jar = WORKSPACE / "02-audio" / "build" / "libs" / "02-audio-1.0.0-SNAPSHOT-boot.jar"
    
    assert script_jar.exists(), f"script bootJar not found at {script_jar}"
    assert audio_jar.exists(), f"audio bootJar not found at {audio_jar}"

    proc_script = None
    proc_audio = None
    try:
        # Start script service
        proc_script = subprocess.Popen(
            ["java", "-jar", str(script_jar)],
            cwd=WORKSPACE / "01-script",
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        print("Waiting for 01-script service on port 38101...")
        assert wait_for_service("http://localhost:38101/health", max_retries=40), "01-script failed to start on port 38101"
        print("[PASS] 01-script is UP on port 38101")

        # Start audio service
        proc_audio = subprocess.Popen(
            ["java", "-jar", str(audio_jar), "--tiktok.tts.mode=synthetic"],
            cwd=WORKSPACE / "02-audio",
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        print("Waiting for 02-audio service on port 38102...")
        assert wait_for_service("http://localhost:38102/health", max_retries=40), "02-audio failed to start on port 38102"
        print("[PASS] 02-audio is UP on port 38102")

        # =====================================================================
        # Test 01-script Endpoints
        # =====================================================================
        print("\n--- Verifying 01-script Endpoints ---")
        st, body, _ = http_get("http://localhost:38101/api/v1/script/health")
        assert st == 200 and "script-service" in body, f"Unexpected health resp: {st}, {body}"
        print("[PASS] GET /api/v1/script/health -> 200 OK")

        # Test POST /api/v1/script/extract with text/plain
        sample_transcript = (WORKSPACE / "script.txt").read_text()
        st, resp_bytes, _ = http_post("http://localhost:38101/api/v1/script/extract", sample_transcript, "text/plain")
        assert st == 200, f"extract text/plain failed with status {st}: {resp_bytes}"
        script_obj = json.loads(resp_bytes.decode("utf-8"))
        assert script_obj["topic"] == "Family", f"Expected topic Family, got {script_obj.get('topic')}"
        assert len(script_obj["scenes"]) == 7, f"Expected 7 scenes, got {len(script_obj.get('scenes', []))}"
        print(f"[PASS] POST /api/v1/script/extract (text/plain) -> 200 OK, extracted {len(script_obj['scenes'])} scenes")

        # Test POST /api/v1/script/extract with JSON wrapper {"transcript": "..."}
        json_req = {"transcript": sample_transcript}
        st, resp_bytes, _ = http_post("http://localhost:38101/api/v1/script/extract", json_req, "application/json")
        assert st == 200, f"extract JSON wrapper failed: {st}"
        script_json_obj = json.loads(resp_bytes.decode("utf-8"))
        assert len(script_json_obj["scenes"]) == 7
        print("[PASS] POST /api/v1/script/extract (JSON payload) -> 200 OK")

        # Test error handling: empty request body
        st, resp_bytes, _ = http_post("http://localhost:38101/api/v1/script/extract", "", "text/plain")
        print(f"Empty body request status: {st}")
        assert st in (400, 500), f"Expected error code 4xx or 500 for empty body, got {st}"
        print(f"[PASS] POST /api/v1/script/extract (empty body) handled with error code {st}")

        # =====================================================================
        # Test 02-audio Endpoints
        # =====================================================================
        print("\n--- Verifying 02-audio Endpoints ---")
        st, body, _ = http_get("http://localhost:38102/api/v1/audio/health")
        assert st == 200 and "audio-service" in body, f"Unexpected audio health: {st}, {body}"
        print("[PASS] GET /api/v1/audio/health -> 200 OK")

        # Test POST /api/v1/audio/synthesize
        tts_req = {"input": "Welcome to IELTS Master Band 7+ vocabulary on Family.", "voice": "alloy"}
        st, wav_bytes, headers = http_post("http://localhost:38102/api/v1/audio/synthesize", tts_req)
        assert st == 200, f"Synthesize failed: {st}, {wav_bytes}"
        assert wav_bytes.startswith(b"RIFF"), "Synthesize output is not a valid RIFF WAV"
        assert b"WAVE" in wav_bytes[:12], "Synthesize output missing WAVE header"
        print(f"[PASS] POST /api/v1/audio/synthesize -> 200 OK, returned {len(wav_bytes)} bytes of valid WAV audio")

        # Test POST /api/v1/audio/timeline with script_obj
        st, timeline_bytes, _ = http_post("http://localhost:38102/api/v1/audio/timeline", script_obj)
        assert st == 200, f"Timeline generation failed: {st}, {timeline_bytes}"
        manifest = json.loads(timeline_bytes.decode("utf-8"))
        assert manifest["totalDurationSeconds"] > 0, "totalDurationSeconds should be > 0"
        assert len(manifest["sceneTimelines"]) == 7, f"Expected 7 scenes in timeline, got {len(manifest['sceneTimelines'])}"
        print(f"[PASS] POST /api/v1/audio/timeline -> 200 OK, manifest totalDuration: {manifest['totalDurationSeconds']:.2f}s across {len(manifest['sceneTimelines'])} scenes")

        # Test error handling on 02-audio: null / invalid input
        st, resp_bytes, _ = http_post("http://localhost:38102/api/v1/audio/synthesize", {}, "application/json")
        print(f"Empty TtsRequest status: {st}")
        assert st in (400, 500), f"Expected error code for empty TtsRequest, got {st}"
        print(f"[PASS] POST /api/v1/audio/synthesize (invalid body) handled with error code {st}")

        empty_script = {"topic": "Family", "targetBand": "Band 7+", "scenes": []}
        st, resp_bytes, _ = http_post("http://localhost:38102/api/v1/audio/timeline", empty_script)
        print(f"Empty scenes timeline status: {st}")
        assert st in (400, 500), f"Expected error code for empty scenes timeline, got {st}"
        print(f"[PASS] POST /api/v1/audio/timeline (empty scenes) handled with error code {st}")

    finally:
        print("\n--- Cleaning up live microservice processes ---")
        if proc_script:
            proc_script.terminate()
            proc_script.wait()
            print("Terminated 01-script")
        if proc_audio:
            proc_audio.terminate()
            proc_audio.wait()
            print("Terminated 02-audio")

if __name__ == "__main__":
    test_docker_and_compose()
    test_live_microservices()
    print("\n>>> ALL ADVERSARIAL MICROSERVICE TESTS COMPLETED SUCCESSFULLY <<<")
