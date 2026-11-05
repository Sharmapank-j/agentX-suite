import subprocess
import time
import urllib.request
import urllib.error
import json
import sys

ADB = r"C:\Users\pr218\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v5.0.1\adb.exe"

def adb(cmd):
    p = subprocess.run(f'"{ADB}" {cmd}', shell=True, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

print("--- Step 1: Waking device and dismissing keyguard ---")
adb("shell \"input keyevent 224; wm dismiss-keyguard; svc power stayon true\"")
time.sleep(1)

print("--- Step 2: Launching AgentX Serve ---")
adb("shell am force-stop com.agentx.admin.debug")
time.sleep(1)
adb("shell am start -n com.agentx.admin.debug/com.agentx.admin.MainActivity")
time.sleep(3)

print("--- Step 3: Tapping Start Server (x=871, y=359) ---")
adb("shell input tap 871 359")
time.sleep(2)

print("--- Step 4: Setting up ADB port forward tcp:8080 ---")
adb("forward tcp:8080 tcp:8080")

print("\n--- Step 5: Querying Endpoints ---")

# 1. /health
try:
    req = urllib.request.Request("http://127.0.0.1:8080/health")
    with urllib.request.urlopen(req, timeout=5) as resp:
        print(f"[SUCCESS] /health: HTTP {resp.status}")
        print("Response:", resp.read().decode('utf-8'))
except Exception as e:
    print(f"[FAIL] /health error: {e}")

# 2. /v1/models (Unauthenticated)
try:
    req = urllib.request.Request("http://127.0.0.1:8080/v1/models")
    urllib.request.urlopen(req, timeout=5)
    print("[FAIL] /v1/models allowed unauthorized access!")
except urllib.error.HTTPError as e:
    print(f"[SUCCESS] Zero-Trust Auth enforced on /v1/models: HTTP {e.code}")

# 3. /v1/models (Authenticated with Master Key)
try:
    req = urllib.request.Request("http://127.0.0.1:8080/v1/models", headers={"Authorization": "Bearer ax-master-serve-key"})
    with urllib.request.urlopen(req, timeout=5) as resp:
        print(f"[SUCCESS] /v1/models authenticated: HTTP {resp.status}")
        data = resp.read().decode('utf-8')
        print("Response snippet:", data[:160] + "...")
except Exception as e:
    print(f"[FAIL] /v1/models authenticated error: {e}")

# 4. /asp/v1/profile (ASP v1.0 specification endpoint)
try:
    req = urllib.request.Request("http://127.0.0.1:8080/asp/v1/profile", headers={"Authorization": "Bearer ax-master-serve-key"})
    with urllib.request.urlopen(req, timeout=5) as resp:
        print(f"[SUCCESS] /asp/v1/profile: HTTP {resp.status}")
        data = resp.read().decode('utf-8')
        print("ASP Device Profile:", data)
except Exception as e:
    print(f"[FAIL] /asp/v1/profile error: {e}")

# 5. /v1/chat/completions (SSE streaming)
try:
    payload = json.dumps({
        "model": "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "messages": [{"role": "user", "content": "Verify Ktor CIO SSE stream."}],
        "stream": True
    }).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:8080/v1/chat/completions",
        data=payload,
        headers={"Authorization": "Bearer ax-master-serve-key", "Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        print(f"[SUCCESS] /v1/chat/completions: HTTP {resp.status}")
        content = resp.read().decode("utf-8")
        print(f"Received {len(content)} characters of SSE chunks.")
        lines = [l for l in content.split("\n") if l.startswith("data:")]
        print("Chunks count:", len(lines))
        print("First 3 chunks:")
        for l in lines[:3]:
            print("  ", l.encode("ascii", "replace").decode("ascii"))
except Exception as e:
    print(f"[FAIL] /v1/chat/completions error: {e}")

print("\n--- Live Ktor Device Verification Finished ---")
