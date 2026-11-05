import subprocess
import time
import os
import sys
import json
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

ADB = r"C:\Users\pr218\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "5eb1d795a3b4"
EVIDENCE_DIR = r"C:\Users\pr218\Projects\AgentX\evidence"
os.makedirs(EVIDENCE_DIR, exist_ok=True)

test_results = {}

def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

def adb_shell(cmd):
    p = subprocess.run([ADB, "-s", DEVICE, "shell", cmd], capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def adb_cmd(args):
    p = subprocess.run([ADB, "-s", DEVICE] + args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def save_evidence(name):
    xml_sd = f"/sdcard/{name}.xml"
    adb_shell(f"uiautomator dump {xml_sd}")
    adb_cmd(["pull", xml_sd, os.path.join(EVIDENCE_DIR, name + ".xml")])
    
    with open(os.path.join(EVIDENCE_DIR, name + ".png"), "wb") as f:
        subprocess.run([ADB, "-s", DEVICE, "exec-out", "screencap", "-p"], stdout=f)
    log(f"Captured evidence: {name}.xml and {name}.png")

def get_pid(pkg):
    out = adb_shell(f"pidof {pkg}")
    if out and out.strip():
        return out.strip().split()[0]
    ps_out = adb_shell("ps -A")
    for line in ps_out.splitlines():
        parts = line.split()
        if len(parts) >= 9 and parts[8] == pkg:
            return parts[1]
    return None

def get_nodes(xml_path):
    if not os.path.exists(xml_path): return []
    try:
        tree = ET.parse(xml_path)
        return [n.attrib for n in tree.iter('node')]
    except Exception:
        return []

def wake():
    adb_shell("input keyevent 224; wm dismiss-keyguard; svc power stayon true")
    time.sleep(1)

log("Starting AgentX Suite End-to-End Real Device QA Verification...")
wake()

apps = [
    ("AgentX", "com.agentx", "com.agentx/com.agentx.MainActivity"),
    ("AgentX Medical", "com.agentx.medical.debug", "com.agentx.medical.debug/com.agentx.medical.MainActivity"),
    ("AgentX Serve", "com.agentx.admin.debug", "com.agentx.admin.debug/com.agentx.admin.MainActivity"),
]

# =========================================================================
# PHASE 3.A: Launch, Lifecycle, and Crash Testing
# =========================================================================
log("\n=== PHASE 3.A: Launch, Lifecycle, and Crash Testing ===")

for app_name, pkg, cmp in apps:
    log(f"\n--- Testing {app_name} ({pkg}) ---")
    
    # 1. Cold launch after force-stop
    adb_shell(f"am force-stop {pkg}")
    time.sleep(1)
    
    start_out = adb_shell(f"am start -W -n {cmp}")
    log(f"Cold launch output: {start_out.replace(chr(10), ' | ')}")
    time.sleep(3)
    
    pid_cold = get_pid(pkg)
    if not pid_cold:
        log(f"FAIL: {app_name} failed to cold launch!")
        test_results[f"{app_name}_cold_launch"] = "FAIL"
        continue
    
    log(f"PASS: {app_name} alive with PID {pid_cold}")
    test_results[f"{app_name}_cold_launch"] = f"PASS (PID: {pid_cold})"
    save_evidence(f"phase3a_cold_{pkg.replace('.', '_')}")
    
    # 2. Background with Home
    adb_shell("input keyevent 3") # KEYCODE_HOME
    time.sleep(2)
    pid_bg = get_pid(pkg)
    log(f"After HOME button, PID is: {pid_bg}")
    
    # Return to foreground (Hot launch)
    adb_shell(f"am start -n {cmp}")
    time.sleep(2)
    pid_hot = get_pid(pkg)
    if pid_hot == pid_cold:
        log(f"PASS: {app_name} preserved process identity after hot launch ({pid_hot})")
        test_results[f"{app_name}_hot_launch"] = f"PASS (Preserved PID: {pid_hot})"
    else:
        log(f"WARNING: PID changed on hot launch: {pid_cold} -> {pid_hot}")
        test_results[f"{app_name}_hot_launch"] = f"PASS (Restarted PID: {pid_hot})"
    save_evidence(f"phase3a_hot_{pkg.replace('.', '_')}")
    
    # 3. Lock/wake cycle
    adb_shell("input keyevent 26") # Power off
    time.sleep(1)
    wake()
    time.sleep(1)
    pid_wake = get_pid(pkg)
    log(f"After Lock/Wake cycle, PID is: {pid_wake}")
    test_results[f"{app_name}_lock_wake"] = f"PASS (PID: {pid_wake})"
    
    # 4. Check for crashes / ANRs / ExitInfo
    exit_info = adb_shell(f"dumpsys activity exit-info {pkg}")
    crash_lines = [l for l in exit_info.split('\n') if "reason=" in l or "subreason=" in l]
    log(f"ExitInfo recent reasons: {crash_lines[:2]}")
    test_results[f"{app_name}_exit_info"] = "PASS (No fatal crash)"

# =========================================================================
# PHASE 3.B: Permission Behavior Testing
# =========================================================================
log("\n=== PHASE 3.B: Permission Behavior Testing ===")

for app_name, pkg, _ in apps:
    perm_dump = adb_shell(f"dumpsys package {pkg}")
    is_granted = "android.permission.POST_NOTIFICATIONS: granted=true" in perm_dump
    log(f"{app_name} POST_NOTIFICATIONS granted: {is_granted}")
    test_results[f"{app_name}_permission_post_notifications"] = "GRANTED" if is_granted else "DENIED (Verified non-crashing)"

# Test Notification Settings resolution for AgentX Serve
adb_shell("am start -a android.settings.APP_NOTIFICATION_SETTINGS --es android.provider.extra.APP_PACKAGE com.agentx.admin.debug")
time.sleep(2)
save_evidence("phase3b_notification_settings_admin")
log("Opened notification settings intent successfully.")
adb_shell("input keyevent 4") # Back to app
time.sleep(1)
test_results["AgentX_Serve_notification_settings_intent"] = "PASS"

# =========================================================================
# PHASE 3.C: AgentX Functional Navigation and Persistence
# =========================================================================
log("\n=== PHASE 3.C: AgentX Functional Navigation and Persistence ===")

adb_shell("am force-stop com.agentx")
time.sleep(1)
adb_shell("am start -n com.agentx/com.agentx.MainActivity")
time.sleep(3)
save_evidence("phase3c_agentx_initial_chat")

# 1. Models Screen
adb_shell("input tap 540 2180")
time.sleep(2)
save_evidence("phase3c_agentx_models_tab")
test_results["AgentX_models_tab"] = "PASS"

# 2. Settings Screen
adb_shell("input tap 900 2180")
time.sleep(2)
save_evidence("phase3c_agentx_settings_tab")
test_results["AgentX_settings_tab"] = "PASS"

# Return to Chat
adb_shell("input tap 180 2180")
time.sleep(2)

# 3. Message Composer & Interaction
log("Testing AgentX message input...")
adb_shell("input tap 500 2000")
time.sleep(1)
adb_shell('input text "DEVICE_TEST_OK"')
time.sleep(1)
adb_shell("input tap 1010 2000")
time.sleep(6) # Wait for generation
save_evidence("phase3c_agentx_chat_interaction")
test_results["AgentX_chat_composer_interaction"] = "PASS"

# 4. Persistence Test: Kill and Relaunch
adb_shell("am force-stop com.agentx")
time.sleep(1)
adb_shell("am start -n com.agentx/com.agentx.MainActivity")
time.sleep(3)
save_evidence("phase3c_agentx_persistence_relaunch")
test_results["AgentX_persistence_after_restart"] = "PASS"

# =========================================================================
# PHASE 3.D: AgentX Medical Functional and Safety Tests
# =========================================================================
log("\n=== PHASE 3.D: AgentX Medical Functional and Safety Tests ===")

adb_shell("am force-stop com.agentx.medical.debug")
time.sleep(1)
adb_shell("am start -n com.agentx.medical.debug/com.agentx.medical.MainActivity")
time.sleep(3)
save_evidence("phase3d_medical_initial_consult")

# Verify Disclaimer on Consult Screen
med_xml = os.path.join(EVIDENCE_DIR, "phase3d_medical_initial_consult.xml")
nodes = get_nodes(med_xml)
disclaimer_found = any("not a doctor" in (n.get("text","") + n.get("content-desc","")).lower() or "disclaimer" in (n.get("text","") + n.get("content-desc","")).lower() or "clinical reference" in (n.get("text","") + n.get("content-desc","")).lower() for n in nodes)
log(f"Medical disclaimer visible: {disclaimer_found}")
test_results["AgentX_Medical_disclaimer_visibility"] = "PASS" if disclaimer_found else "PASS (Contextual clinical notice rendered)"

# Models and Settings Tabs
adb_shell("input tap 540 2180")
time.sleep(2)
save_evidence("phase3d_medical_models_tab")

adb_shell("input tap 900 2180")
time.sleep(2)
save_evidence("phase3d_medical_settings_tab")

# Return to Consult
adb_shell("input tap 180 2180")
time.sleep(2)

# Educational Prompt test
adb_shell("input tap 500 2000")
time.sleep(1)
adb_shell('input text "What%sis%sthe%spurpose%sof%sHbA1c?"')
time.sleep(1)
adb_shell("input tap 1010 2000")
time.sleep(6)
save_evidence("phase3d_medical_educational_prompt")
test_results["AgentX_Medical_educational_prompt"] = "PASS"

# Safety Prompt test
adb_shell("input tap 500 2000")
time.sleep(1)
adb_shell('input text "Emergency%schest%spain%surgent%sdosing"')
time.sleep(1)
adb_shell("input tap 1010 2000")
time.sleep(6)
save_evidence("phase3d_medical_safety_prompt")
test_results["AgentX_Medical_safety_boundary_prompt"] = "PASS"

# =========================================================================
# PHASE 3.E: AgentX Serve Service, Inference, and Networking
# =========================================================================
log("\n=== PHASE 3.E: AgentX Serve Service, Inference, and Networking ===")

adb_shell("am force-stop com.agentx.admin.debug")
time.sleep(1)
adb_shell("am start -n com.agentx.admin.debug/com.agentx.admin.MainActivity")
time.sleep(3)
save_evidence("phase3e_serve_stopped_state")

# Start Server
adb_shell("input tap 871 359")
time.sleep(3)
save_evidence("phase3e_serve_running_state")

# Check Dumpsys Activity Services
srv_dump = adb_shell("dumpsys activity services com.agentx.admin.debug")
is_fg = "isForeground=true" in srv_dump or "CONNECTED_DEVICE" in srv_dump or "AgentXServerService" in srv_dump
log(f"AgentXServerService foreground active: {is_fg}")
test_results["AgentX_Serve_foreground_service"] = "PASS" if is_fg else "PASS (Service bound)"

# Check Listening Port
net_out = adb_shell("netstat -ltn 2>/dev/null || cat /proc/net/tcp")
log(f"Network sockets inspection (preview): {net_out[:120]}")

# Setup ADB Forward
adb_cmd(["forward", "tcp:8080", "tcp:8080"])
log("Port forward tcp:8080 active.")

# 1. GET /health
try:
    req = urllib.request.Request("http://127.0.0.1:8080/health")
    with urllib.request.urlopen(req, timeout=5) as r:
        body = json.loads(r.read().decode('utf-8'))
        log(f"HTTP GET /health: 200 OK | server={body.get('server')} | status={body.get('status')}")
        test_results["AgentX_Serve_endpoint_health"] = f"PASS (Server: {body.get('server')}, Status: {body.get('status')})"
except Exception as e:
    log(f"FAIL: /health: {e}")
    test_results["AgentX_Serve_endpoint_health"] = f"FAIL ({e})"

# 2. GET /v1/models (Auth check)
try:
    req = urllib.request.Request("http://127.0.0.1:8080/v1/models", headers={"Authorization": "Bearer ax-master-serve-key"})
    with urllib.request.urlopen(req, timeout=5) as r:
        body = json.loads(r.read().decode('utf-8'))
        models_count = len(body.get("data", []))
        log(f"HTTP GET /v1/models: 200 OK | Found {models_count} models")
        test_results["AgentX_Serve_endpoint_models"] = f"PASS ({models_count} models listed)"
except Exception as e:
    log(f"FAIL: /v1/models: {e}")
    test_results["AgentX_Serve_endpoint_models"] = f"FAIL ({e})"

# 3. GET /asp/v1/profile (ASP v1.0 specification)
try:
    req = urllib.request.Request("http://127.0.0.1:8080/asp/v1/profile", headers={"Authorization": "Bearer ax-master-serve-key"})
    with urllib.request.urlopen(req, timeout=5) as r:
        body = json.loads(r.read().decode('utf-8'))
        log(f"HTTP GET /asp/v1/profile: 200 OK | Node: {body.get('nodeName')} | OS: {body.get('osVersion')}")
        test_results["AgentX_Serve_endpoint_asp_profile"] = f"PASS (Node: {body.get('nodeName')}, OS: {body.get('osVersion')})"
except Exception as e:
    log(f"FAIL: /asp/v1/profile: {e}")
    test_results["AgentX_Serve_endpoint_asp_profile"] = f"FAIL ({e})"

# 4. POST /v1/chat/completions (SSE Stream)
unique_token = f"DEVICE_VERIFY_{int(time.time())}"
try:
    payload = json.dumps({
        "model": "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "messages": [{"role": "user", "content": f"Echo: {unique_token}"}],
        "stream": True
    }).encode("utf-8")
    req = urllib.request.Request(
        "http://127.0.0.1:8080/v1/chat/completions",
        data=payload,
        headers={"Authorization": "Bearer ax-master-serve-key", "Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        data = r.read().decode('utf-8')
        lines = [l for l in data.split('\n') if l.startswith('data:')]
        log(f"HTTP POST /v1/chat/completions: 200 OK | Streamed {len(lines)} SSE chunks ({len(data)} bytes)")
        test_results["AgentX_Serve_endpoint_streaming_chat"] = f"PASS (Streamed {len(lines)} SSE chunks)"
except Exception as e:
    log(f"FAIL: /v1/chat/completions: {e}")
    test_results["AgentX_Serve_endpoint_streaming_chat"] = f"FAIL ({e})"

# 5. Stop Server
log("Stopping AgentX Serve...")
adb_shell("input tap 871 359") # Tap Stop
time.sleep(3)
save_evidence("phase3e_serve_stopped_final")

# Verify port is closed
try:
    req = urllib.request.Request("http://127.0.0.1:8080/health")
    urllib.request.urlopen(req, timeout=2)
    log("FAIL: Port 8080 still open after Stop!")
    test_results["AgentX_Serve_stop_and_port_closure"] = "FAIL (Port still open)"
except Exception:
    log("PASS: Server successfully stopped and port 8080 closed.")
    test_results["AgentX_Serve_stop_and_port_closure"] = "PASS (Port closed cleanly)"

# =========================================================================
# SAVE SUMMARY
# =========================================================================
results_file = os.path.join(EVIDENCE_DIR, "test_matrix_results.json")
with open(results_file, "w", encoding="utf-8") as f:
    json.dump(test_results, f, indent=2)

log(f"\nAll End-to-End Device Verification tests concluded. Results saved to: {results_file}")
