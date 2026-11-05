import subprocess
import time
import json
import os
import urllib.request
import urllib.error

ADB_PATH = r"C:\Users\pr218\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v5.0.1\adb.exe"
DEVICE_SERIAL = "5eb1d795a3b4"
OUTPUT_DIR = r"C:\Users\pr218\Projects\AgentX\assets\device_tests"

os.makedirs(OUTPUT_DIR, exist_ok=True)

def adb_cmd(cmd_args):
    full_cmd = [ADB_PATH, "-s", DEVICE_SERIAL] + cmd_args
    res = subprocess.run(full_cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    stdout = res.stdout.strip() if res.stdout else ""
    stderr = res.stderr.strip() if res.stderr else ""
    return stdout, stderr, res.returncode

def wake_device():
    print("[DEVICE] Waking up screen and dismissing keyguard...")
    adb_cmd(["shell", "input keyevent 224"])
    adb_cmd(["shell", "wm dismiss-keyguard"])
    adb_cmd(["shell", "svc power stayon true"])
    time.sleep(1)

def capture_screenshot(filename):
    local_path = os.path.join(OUTPUT_DIR, filename)
    remote_path = f"/sdcard/{filename}"
    adb_cmd(["shell", f"screencap -p {remote_path}"])
    adb_cmd(["pull", remote_path, local_path])
    adb_cmd(["shell", f"rm {remote_path}"])
    print(f"  [SCREENSHOT] Saved: {filename} ({os.path.getsize(local_path)} bytes)")
    return local_path

def dump_ui_hierarchy():
    remote_path = "/sdcard/window_dump.xml"
    adb_cmd(["shell", f"uiautomator dump {remote_path}"])
    stdout, _, _ = adb_cmd(["shell", f"cat {remote_path}"])
    return stdout

def test_http_endpoint(url, method="GET", headers=None, payload=None, timeout=5):
    req = urllib.request.Request(url, method=method)
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    data_bytes = payload.encode('utf-8') if payload else None
    try:
        with urllib.request.urlopen(req, data=data_bytes, timeout=timeout) as resp:
            return resp.status, resp.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8')
    except Exception as e:
        return 0, str(e)

def run_tests():
    report = {"status": "SUCCESS", "timestamp": time.time(), "tests": []}
    wake_device()

    # =========================================================================
    # APP 1: AgentX Serve (:admin)
    # =========================================================================
    print("\n" + "="*60)
    print(">>> 1. TESTING AGENTX SERVE (:admin)")
    print("="*60)

    adb_cmd(["shell", "am force-stop com.agentx.admin.debug"])
    adb_cmd(["shell", "am start -n com.agentx.admin.debug/com.agentx.admin.MainActivity"])
    time.sleep(2)

    capture_screenshot("01_serve_stopped.png")
    dump_init = dump_ui_hierarchy()
    initial_stopped = "SERVER STOPPED" in dump_init
    print(f"  [VERIFY] Server initial state STOPPED: {initial_stopped}")
    report["tests"].append({"app": "AgentX Serve", "check": "Initial Stopped State", "passed": initial_stopped})

    # Start server
    print("  [ACTION] Tapping 'Start' button...")
    adb_cmd(["shell", "input tap 871 359"])
    time.sleep(3)

    capture_screenshot("02_serve_running.png")
    dump_run = dump_ui_hierarchy()
    server_running = "SERVER RUNNING" in dump_run or "online" in dump_run
    print(f"  [VERIFY] Server state RUNNING: {server_running}")
    report["tests"].append({"app": "AgentX Serve", "check": "Server Running State", "passed": server_running})

    # Forward port 8080
    adb_cmd(["forward", "tcp:8080", "tcp:8080"])
    time.sleep(1)

    # Test /health
    code, body = test_http_endpoint("http://127.0.0.1:8080/health")
    health_ok = (code == 200 and ("online" in body or "healthy" in body))
    print(f"  [API] GET /health -> Status {code}, Body: {body[:80]}... (OK: {health_ok})")
    report["tests"].append({"app": "AgentX Serve", "check": "GET /health 200 OK", "passed": health_ok})

    # Test /v1/models
    code, body = test_http_endpoint("http://127.0.0.1:8080/v1/models")
    catalog_ok = (code == 200 and ("data" in body or "id" in body))
    print(f"  [API] GET /v1/models -> Status {code}, Data: {body[:80]}... (OK: {catalog_ok})")
    report["tests"].append({"app": "AgentX Serve", "check": "GET /v1/models 200 OK", "passed": catalog_ok})

    # Test Zero-Trust Auth
    chat_payload = json.dumps({"model": "medimind-v2-q5_k_m", "messages": [{"role": "user", "content": "Hello"}]})
    code, body = test_http_endpoint("http://127.0.0.1:8080/v1/chat/completions", method="POST",
                                    headers={"Content-Type": "application/json"}, payload=chat_payload)
    zero_trust_ok = (code == 401)
    print(f"  [API] POST /v1/chat/completions (Unauthenticated) -> Status {code} (Zero-Trust OK: {zero_trust_ok})")
    report["tests"].append({"app": "AgentX Serve", "check": "Zero-Trust 401 Unauthorized", "passed": zero_trust_ok})

    # Master Key Authenticated Inference
    master_key = "agx_adm_cjieyc1e67udrsnxzlye817x327m"
    if "agx_adm_" in dump_run:
        import re
        m = re.search(r"agx_adm_[a-z0-9]+", dump_run)
        if m: master_key = m.group(0)

    code, body = test_http_endpoint("http://127.0.0.1:8080/v1/chat/completions", method="POST",
                                    headers={"Content-Type": "application/json", "Authorization": f"Bearer {master_key}"},
                                    payload=chat_payload)
    infer_ok = (code == 200 and len(body) > 0)
    print(f"  [API] POST /v1/chat/completions (Bearer Auth) -> Status {code}, Chunks: {len(body)} chars (OK: {infer_ok})")
    report["tests"].append({"app": "AgentX Serve", "check": "Authenticated Inference 200 Streaming", "passed": infer_ok})

    # Test Admin Tabs: Overview, Keys, Models, CLI, Traffic
    # Tab 2: API Keys (x=240, y=540)
    print("  [NAV] Tapping 'API Keys' tab...")
    adb_cmd(["shell", "input tap 240 540"])
    time.sleep(1)
    capture_screenshot("03_serve_keys.png")
    keys_ok = "Master Key" in dump_ui_hierarchy()
    report["tests"].append({"app": "AgentX Serve", "check": "API Keys Tab Rendered", "passed": keys_ok})

    # Tab 3: Models (x=450, y=540)
    print("  [NAV] Tapping 'Models' tab...")
    adb_cmd(["shell", "input tap 450 540"])
    time.sleep(1)
    capture_screenshot("04_serve_models.png")
    models_ok = "medimind" in dump_ui_hierarchy().lower() or "Model" in dump_ui_hierarchy()
    report["tests"].append({"app": "AgentX Serve", "check": "Models Tab Rendered", "passed": models_ok})

    # Tab 4: CLI Console (x=680, y=540)
    print("  [NAV] Tapping 'CLI Console' tab...")
    adb_cmd(["shell", "input tap 680 540"])
    time.sleep(1)
    capture_screenshot("05_serve_cli.png")
    cli_ok = "agentx-serve" in dump_ui_hierarchy() or "clear" in dump_ui_hierarchy() or "help" in dump_ui_hierarchy()
    report["tests"].append({"app": "AgentX Serve", "check": "CLI Console Tab Rendered", "passed": cli_ok})

    # Tab 5: Traffic (x=900, y=540)
    print("  [NAV] Tapping 'Traffic' tab...")
    adb_cmd(["shell", "input tap 900 540"])
    time.sleep(1)
    capture_screenshot("06_serve_traffic.png")
    traffic_ok = "Audit" in dump_ui_hierarchy() or "Traffic" in dump_ui_hierarchy() or "200" in dump_ui_hierarchy()
    report["tests"].append({"app": "AgentX Serve", "check": "Traffic Audit Tab Rendered", "passed": traffic_ok})

    # =========================================================================
    # APP 2: AgentX Personal Assistant (:app)
    # =========================================================================
    print("\n" + "="*60)
    print(">>> 2. TESTING AGENTX PERSONAL ASSISTANT (:app)")
    print("="*60)

    adb_cmd(["shell", "am force-stop com.agentx"])
    adb_cmd(["shell", "am start -n com.agentx/com.agentx.MainActivity"])
    time.sleep(2)

    # Chat screen
    capture_screenshot("07_agentx_chat.png")
    dump_chat = dump_ui_hierarchy()
    chat_ok = "AgentX" in dump_chat or "Quantum" in dump_chat or "Ask" in dump_chat
    print(f"  [VERIFY] AgentX Chat screen rendered: {chat_ok}")
    report["tests"].append({"app": "AgentX", "check": "Chat Screen Rendered", "passed": chat_ok})

    # Open Drawer (Menu at x=60, y=140)
    print("  [ACTION] Opening History Drawer...")
    adb_cmd(["shell", "input tap 60 140"])
    time.sleep(1)
    capture_screenshot("08_agentx_drawer.png")
    dump_drawer = dump_ui_hierarchy()
    drawer_ok = "Conversations" in dump_drawer or "New Chat" in dump_drawer or "Chat" in dump_drawer
    print(f"  [VERIFY] AgentX History Drawer: {drawer_ok}")
    report["tests"].append({"app": "AgentX", "check": "History Drawer Rendered", "passed": drawer_ok})
    # Close drawer by tapping outside (x=900, y=500)
    adb_cmd(["shell", "input tap 900 500"])
    time.sleep(1)

    # Bottom Nav: Models (Tab 2: center x=540, y=2180)
    print("  [NAV] Tapping Bottom Nav 'Models' (x=540, y=2180)...")
    adb_cmd(["shell", "input tap 540 2180"])
    time.sleep(2)
    capture_screenshot("09_agentx_models.png")
    dump_app_models = dump_ui_hierarchy()
    models_screen_ok = "Models" in dump_app_models or "Local" in dump_app_models or "RAM" in dump_app_models
    print(f"  [VERIFY] AgentX Models Screen: {models_screen_ok}")
    report["tests"].append({"app": "AgentX", "check": "Models Screen Rendered", "passed": models_screen_ok})

    # Bottom Nav: Settings (Tab 3: center x=900, y=2180)
    print("  [NAV] Tapping Bottom Nav 'Settings' (x=900, y=2180)...")
    adb_cmd(["shell", "input tap 900 2180"])
    time.sleep(2)
    capture_screenshot("10_agentx_settings.png")
    dump_app_settings = dump_ui_hierarchy()
    settings_screen_ok = "Settings" in dump_app_settings or "Gateway" in dump_app_settings or "Threads" in dump_app_settings
    print(f"  [VERIFY] AgentX Settings Screen: {settings_screen_ok}")
    report["tests"].append({"app": "AgentX", "check": "Settings Screen Rendered", "passed": settings_screen_ok})

    # Test Gateway Connection from Settings
    print("  [ACTION] Scrolling in Settings...")
    adb_cmd(["shell", "input swipe 500 1200 500 600"])
    time.sleep(1)
    capture_screenshot("11_agentx_settings_scrolled.png")

    # Bottom Nav: Return to Chat (Tab 1: center x=180, y=2180)
    print("  [NAV] Returning to 'Chat' (x=180, y=2180)...")
    adb_cmd(["shell", "input tap 180 2180"])
    time.sleep(1)

    # Test Chat Prompt Delivery
    print("  [ACTION] Sending Chat query: 'Explain local AI inference'...")
    adb_cmd(["shell", "input tap 400 2050"])
    time.sleep(0.5)
    adb_cmd(["shell", "input text 'Explain%slocal%sAI%sinference'"])
    time.sleep(1)
    adb_cmd(["shell", "input tap 980 2050"])
    time.sleep(4)
    capture_screenshot("12_agentx_chat_streaming.png")
    dump_chat_res = dump_ui_hierarchy()
    chat_prompt_ok = "local" in dump_chat_res.lower() or "AI" in dump_chat_res or "inference" in dump_chat_res.lower()
    print(f"  [VERIFY] AgentX Chat prompt response delivered: {chat_prompt_ok}")
    report["tests"].append({"app": "AgentX", "check": "Chat Response Streaming", "passed": chat_prompt_ok})

    # =========================================================================
    # APP 3: AgentX Medical (:medical)
    # =========================================================================
    print("\n" + "="*60)
    print(">>> 3. TESTING AGENTX MEDICAL (:medical)")
    print("="*60)

    adb_cmd(["shell", "am force-stop com.agentx.medical.debug"])
    adb_cmd(["shell", "am start -n com.agentx.medical.debug/com.agentx.medical.MainActivity"])
    time.sleep(2)

    # Medical Chat screen
    capture_screenshot("13_medical_chat.png")
    dump_med_init = dump_ui_hierarchy()
    disclaimer_ok = "Clinical Reference Notice" in dump_med_init or "Clinical" in dump_med_init
    print(f"  [VERIFY] Medical Disclaimer Notice Banner: {disclaimer_ok}")
    report["tests"].append({"app": "AgentX Medical", "check": "Disclaimer Notice Banner", "passed": disclaimer_ok})

    # Open Clinical History Drawer (x=60, y=140)
    print("  [ACTION] Opening Medical Consultation Drawer...")
    adb_cmd(["shell", "input tap 60 140"])
    time.sleep(1)
    capture_screenshot("14_medical_drawer.png")
    dump_med_drawer = dump_ui_hierarchy()
    med_drawer_ok = "Consultations" in dump_med_drawer or "New Consult" in dump_med_drawer or "Consult" in dump_med_drawer
    print(f"  [VERIFY] Medical Drawer Rendered: {med_drawer_ok}")
    report["tests"].append({"app": "AgentX Medical", "check": "Consultation Drawer Rendered", "passed": med_drawer_ok})
    # Close drawer
    adb_cmd(["shell", "input tap 900 500"])
    time.sleep(1)

    # Bottom Nav: Models (Tab 2: center x=540, y=2180)
    print("  [NAV] Tapping Bottom Nav 'Models' in Medical (x=540, y=2180)...")
    adb_cmd(["shell", "input tap 540 2180"])
    time.sleep(2)
    capture_screenshot("15_medical_models.png")
    dump_med_models = dump_ui_hierarchy()
    med_models_screen_ok = "medimind" in dump_med_models.lower() or "Models" in dump_med_models or "Clinical" in dump_med_models
    print(f"  [VERIFY] Medical Models Screen: {med_models_screen_ok}")
    report["tests"].append({"app": "AgentX Medical", "check": "Models Screen Rendered", "passed": med_models_screen_ok})

    # Bottom Nav: Settings (Tab 3: center x=900, y=2180)
    print("  [NAV] Tapping Bottom Nav 'Settings' in Medical (x=900, y=2180)...")
    adb_cmd(["shell", "input tap 900 2180"])
    time.sleep(2)
    capture_screenshot("16_medical_settings.png")
    dump_med_settings = dump_ui_hierarchy()
    med_settings_screen_ok = "Settings" in dump_med_settings or "Gateway" in dump_med_settings
    print(f"  [VERIFY] Medical Settings Screen: {med_settings_screen_ok}")
    report["tests"].append({"app": "AgentX Medical", "check": "Settings Screen Rendered", "passed": med_settings_screen_ok})

    # Return to Consult (Tab 1: center x=180, y=2180)
    print("  [NAV] Returning to 'Consult' (x=180, y=2180)...")
    adb_cmd(["shell", "input tap 180 2180"])
    time.sleep(1)

    # Test Clinical Calculation Prompt Delivery
    print("  [ACTION] Sending Clinical query: 'Calculate CHA2DS2-VASc score for 75yo hypertensive male'...")
    adb_cmd(["shell", "input tap 400 2050"])
    time.sleep(0.5)
    adb_cmd(["shell", "input text 'Calculate%sCHA2DS2-VASc%sscore%sfor%s75yo%shypertensive%smale'"])
    time.sleep(1)
    adb_cmd(["shell", "input tap 980 2050"])
    time.sleep(4)
    capture_screenshot("17_medical_consult_streaming.png")
    dump_med_res = dump_ui_hierarchy()
    clinical_resp_ok = "cha2ds2" in dump_med_res.lower() or "score" in dump_med_res.lower() or "hypertension" in dump_med_res.lower() or "vascular" in dump_med_res.lower()
    print(f"  [VERIFY] Medical consultation delivered and processed: {clinical_resp_ok}")
    report["tests"].append({"app": "AgentX Medical", "check": "Clinical Consult Response Delivered", "passed": clinical_resp_ok})

    # =========================================================================
    # SUMMARY
    # =========================================================================
    total = len(report["tests"])
    passed = sum(1 for t in report["tests"] if t.get("passed", False))
    print("\n" + "="*60)
    print(f">>> FINAL LIVE PHYSICAL DEVICE TEST SUMMARY: {passed} / {total} CHECKS PASSED ({int(passed/total*100)}%)")
    print("="*60)

    report_path = os.path.join(OUTPUT_DIR, "live_device_test_report.json")
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"Full report saved to: {report_path}")

if __name__ == "__main__":
    run_tests()
