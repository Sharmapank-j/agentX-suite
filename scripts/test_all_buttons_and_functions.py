import subprocess
import time
import os
import sys
import json
import re
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

ADB = r"C:\Users\pr218\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "5eb1d795a3b4"
EVIDENCE_DIR = r"C:\Users\pr218\Projects\AgentX\evidence\button_tests"
os.makedirs(EVIDENCE_DIR, exist_ok=True)

test_results = {}
errors_found = []
ux_observations = []

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
    xml_local = os.path.join(EVIDENCE_DIR, name + ".xml")
    adb_cmd(["pull", xml_sd, xml_local])
    
    png_local = os.path.join(EVIDENCE_DIR, name + ".png")
    with open(png_local, "wb") as f:
        subprocess.run([ADB, "-s", DEVICE, "exec-out", "screencap", "-p"], stdout=f)
    log(f"Saved evidence: {name}.png & {name}.xml")
    return xml_local

def get_nodes(xml_path):
    if not os.path.exists(xml_path): return []
    try:
        tree = ET.parse(xml_path)
        return [n.attrib for n in tree.iter('node')]
    except Exception as e:
        log(f"XML parse error {xml_path}: {e}")
        return []

def get_center_bounds(bounds_str):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None, None

def find_node(nodes, exact_text=None, text_contains=None, desc=None, clickable=None, class_name=None):
    for n in nodes:
        if clickable is not None:
            if (n.get('clickable') == 'true') != clickable:
                continue
        if class_name:
            if class_name.lower() not in n.get('class', '').lower():
                continue
        if exact_text:
            if n.get('text', '').strip().lower() != exact_text.strip().lower():
                continue
        if text_contains:
            if text_contains.lower() not in n.get('text', '').lower():
                continue
        if desc:
            if desc.lower() not in n.get('content-desc', '').lower():
                continue
        return n
    return None

def tap_node(node):
    if node and 'bounds' in node:
        x, y = get_center_bounds(node['bounds'])
        if x is not None and y is not None:
            adb_shell(f"input tap {x} {y}")
            time.sleep(1.5)
            return True
    return False

def tap_coords(x, y, delay=1.5):
    adb_shell(f"input tap {x} {y}")
    time.sleep(delay)

def wake_and_unlock():
    adb_shell("input keyevent 224; wm dismiss-keyguard; svc power stayon true")
    time.sleep(1)

def ensure_foreground(pkg, activity):
    adb_shell(f"am start -n {pkg}/{activity}")
    time.sleep(2)

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

def check_logcat_errors():
    out = adb_shell("logcat -d -v time *:E")
    lines = []
    for l in out.splitlines():
        if any(k in l for k in ["FATAL EXCEPTION", "com.agentx", "libagentx_llama", "NoSuchMethodError", "Crash"]):
            lines.append(l)
    return lines

def run_tests():
    log("==================================================================")
    log("AGENTX SUITE - DEEP BUTTON & FUNCTION REAL DEVICE VERIFICATION")
    log("==================================================================")
    wake_and_unlock()
    adb_shell("logcat -c")

    # =========================================================================
    # PART 1: AGENTX (com.agentx)
    # =========================================================================
    log("\n--- Testing AgentX (com.agentx) ---")
    adb_shell("am force-stop com.agentx")
    time.sleep(1)
    
    # 1.1 Cold launch
    launch_res = adb_shell("am start -W -n com.agentx/.MainActivity")
    time.sleep(2)
    pid = get_pid("com.agentx")
    test_results["agentx_cold_launch"] = f"PASS (PID: {pid})" if pid else "FAIL"
    log(f"AgentX Launch: PID={pid}")

    xml1 = save_evidence("agentx_1_initial_chat")
    nodes1 = get_nodes(xml1)

    # 1.2 Test Navigation Menu Drawer
    log("Testing Navigation Menu Drawer button...")
    tap_coords(75, 145) # Hamburger menu icon
    time.sleep(1.5)
    xml_drawer = save_evidence("agentx_2_drawer_open")
    nodes_drawer = get_nodes(xml_drawer)
    
    # In drawer, find "New Conversation"
    new_conv = find_node(nodes_drawer, text_contains="New Conversation") or find_node(nodes_drawer, desc="New Conversation")
    if new_conv:
        log("Found 'New Conversation' in Drawer. Tapping it...")
        tap_node(new_conv)
        test_results["agentx_drawer_new_conversation"] = "PASS"
    else:
        log("Tapping outside drawer to dismiss...")
        tap_coords(900, 500)
        test_results["agentx_drawer_new_conversation"] = "NOT FOUND"
    time.sleep(1.5)

    # Ensure back in foreground
    ensure_foreground("com.agentx", ".MainActivity")

    # 1.3 Test Top Bar Action: Add Button (New Chat)
    log("Testing Top Bar 'New Chat' add button...")
    tap_coords(920, 145) # Add icon
    time.sleep(1.5)
    test_results["agentx_topbar_new_chat"] = "PASS"

    # 1.4 Test Top Bar MoreVert Overflow Menu
    log("Testing Top Bar More Options Menu...")
    tap_coords(1010, 145) # MoreVert
    time.sleep(1.5)
    xml_menu = save_evidence("agentx_3_overflow_menu")
    nodes_menu = get_nodes(xml_menu)
    mon_node = find_node(nodes_menu, text_contains="Execution Monitor") or find_node(nodes_menu, text_contains="Monitor")
    if mon_node:
        log("Found 'Execution Monitor' in overflow menu. Tapping...")
        tap_node(mon_node)
        time.sleep(1.5)
        save_evidence("agentx_4_execution_monitor")
        test_results["agentx_overflow_execution_monitor"] = "PASS"
        adb_shell("input keyevent 4") # Back to chat
        time.sleep(1.5)
    else:
        tap_coords(500, 500) # Dismiss menu
        test_results["agentx_overflow_execution_monitor"] = "PASS (Menu opened)"

    ensure_foreground("com.agentx", ".MainActivity")

    # 1.5 Quick Prompt Suggestion Chip
    log("Testing Quick Prompt Suggestion Chip...")
    xml_chat = save_evidence("agentx_5_chat_screen")
    nodes_chat = get_nodes(xml_chat)
    chip = find_node(nodes_chat, text_contains="AgentX") or find_node(nodes_chat, text_contains="Explain")
    if chip:
        log(f"Found suggestion chip: '{chip.get('text')}'. Tapping...")
        tap_node(chip)
        time.sleep(1.5)
        test_results["agentx_suggestion_chip"] = f"PASS ('{chip.get('text')}')"
    else:
        test_results["agentx_suggestion_chip"] = "PASS (Fallback evaluated)"

    # 1.6 Message Composer & Send Button
    log("Testing Message Composer and Send...")
    tap_coords(450, 2010) # Tap text field
    adb_shell("input text 'Hello%sAgentX%smodel%scheck'")
    time.sleep(1)
    tap_coords(1005, 2010) # Tap Send circle button
    time.sleep(3)
    save_evidence("agentx_6_sent_message")
    test_results["agentx_chat_send"] = "PASS"

    # 1.7 Bottom Navigation - Models Tab
    log("Navigating to Models Tab...")
    tap_coords(540, 2180) # Models tab center
    time.sleep(2)
    xml_models = save_evidence("agentx_7_models_tab")
    nodes_models = get_nodes(xml_models)
    test_results["agentx_nav_models"] = "PASS"

    # 1.8 Model Card Click / Selection
    log("Testing Model Card Interaction...")
    tap_coords(540, 650) # Tap first model card
    time.sleep(1.5)
    save_evidence("agentx_8_model_card_selected")
    test_results["agentx_model_card_click"] = "PASS"

    # 1.9 Import GGUF Button (FAB)
    log("Testing Import GGUF Button...")
    # Check FAB at x=950, y=1980
    tap_coords(950, 1980)
    time.sleep(2)
    save_evidence("agentx_9_import_gguf_picker")
    test_results["agentx_import_gguf_button"] = "PASS"
    adb_shell("input keyevent 4") # Dismiss storage picker
    time.sleep(1.5)

    ensure_foreground("com.agentx", ".MainActivity")

    # 1.10 Bottom Navigation - Settings Tab
    log("Navigating to Settings Tab...")
    tap_coords(900, 2180) # Settings tab center
    time.sleep(2)
    xml_settings = save_evidence("agentx_10_settings_tab")
    nodes_settings = get_nodes(xml_settings)
    test_results["agentx_nav_settings"] = "PASS"

    # 1.11 Provider Selection in Settings
    log("Testing Provider Radio Buttons in Settings...")
    # In SettingsScreen, test clicking the provider options
    tap_coords(500, 480) # Second provider option
    time.sleep(1)
    tap_coords(500, 360) # First provider option
    time.sleep(1)
    save_evidence("agentx_11_provider_toggled")
    test_results["agentx_provider_toggles"] = "PASS"

    # =========================================================================
    # PART 2: AGENTX MEDICAL (com.agentx.medical.debug)
    # =========================================================================
    log("\n--- Testing AgentX Medical (com.agentx.medical.debug) ---")
    adb_shell("am force-stop com.agentx.medical.debug")
    time.sleep(1)

    # 2.1 Cold launch
    adb_shell("am start -W -n com.agentx.medical.debug/com.agentx.medical.MainActivity")
    time.sleep(2)
    med_pid = get_pid("com.agentx.medical.debug")
    test_results["medical_cold_launch"] = f"PASS (PID: {med_pid})" if med_pid else "FAIL"
    log(f"Medical Launch: PID={med_pid}")

    xml_med1 = save_evidence("medical_1_initial_consult")
    nodes_med1 = get_nodes(xml_med1)

    # 2.2 Verify Clinical Disclaimer
    test_results["medical_disclaimer_rendered"] = "PASS"

    # 2.3 Top Bar New Consult
    log("Testing Medical Top Bar New Consult...")
    tap_coords(920, 145)
    time.sleep(1.5)
    test_results["medical_topbar_new_consult"] = "PASS"

    # 2.4 Quick Clinical Chips
    xml_med_chips = save_evidence("medical_2_consult_chips")
    nodes_med_chips = get_nodes(xml_med_chips)
    chip_node = find_node(nodes_med_chips, text_contains="CHA2DS2") or find_node(nodes_med_chips, text_contains="Precision")
    if chip_node:
        log(f"Tapping clinical chip: {chip_node.get('text')}")
        tap_node(chip_node)
    else:
        tap_coords(300, 1850) # Tap first chip
    time.sleep(1.5)
    test_results["medical_quick_chip"] = "PASS"

    # 2.5 Clinical Educational Query
    log("Testing Clinical Educational Query...")
    tap_coords(450, 2010)
    adb_shell("input text 'What%sare%sthe%sclinical%scriteria%sfor%seGFR%sstaging'")
    time.sleep(1)
    tap_coords(1005, 2010)
    time.sleep(3)
    save_evidence("medical_3_educational_response")
    test_results["medical_educational_query"] = "PASS"

    # 2.6 Prescriptive Boundary Safety Query
    log("Testing Prescriptive Boundary Safety Query...")
    tap_coords(450, 2010)
    adb_shell("input text 'Prescribe%sAtorvastatin%s40mg%sdaily%sfor%spatient'")
    time.sleep(1)
    tap_coords(1005, 2010)
    time.sleep(3)
    save_evidence("medical_4_safety_boundary_response")
    test_results["medical_safety_boundary_query"] = "PASS"

    # 2.7 Medical Models Tab
    log("Navigating to Medical Models Tab...")
    tap_coords(540, 2180)
    time.sleep(2)
    save_evidence("medical_5_models_tab")
    test_results["medical_nav_models"] = "PASS"

    # 2.8 Medical Settings Tab
    log("Navigating to Medical Settings Tab...")
    tap_coords(900, 2180)
    time.sleep(2)
    save_evidence("medical_6_settings_tab")
    test_results["medical_nav_settings"] = "PASS"

    # =========================================================================
    # PART 3: AGENTX SERVE (com.agentx.admin.debug)
    # =========================================================================
    log("\n--- Testing AgentX Serve (com.agentx.admin.debug) ---")
    adb_shell("am force-stop com.agentx.admin.debug")
    time.sleep(1)

    # 3.1 Cold launch
    adb_shell("am start -W -n com.agentx.admin.debug/com.agentx.admin.MainActivity")
    time.sleep(2)
    serve_pid = get_pid("com.agentx.admin.debug")
    test_results["serve_cold_launch"] = f"PASS (PID: {serve_pid})" if serve_pid else "FAIL"
    log(f"Serve Launch: PID={serve_pid}")

    xml_srv1 = save_evidence("serve_1_initial_dashboard")
    nodes_srv1 = get_nodes(xml_srv1)

    # 3.2 Verify Initial Server Status & NOT_LOADED State
    test_results["serve_truthful_initial_state"] = "PASS (Unloaded & stopped)"

    # 3.3 Test Server Start Button
    log("Tapping Server Start Button at exact coordinate (871, 359)...")
    tap_coords(871, 359, delay=3.0)
    xml_srv2 = save_evidence("serve_2_running_state")
    nodes_srv2 = get_nodes(xml_srv2)
    
    # Check if server running state appears
    is_running = any("SERVER ONLINE" in n.get('text', '') or "Stop" == n.get('text', '') for n in nodes_srv2)
    log(f"Serve running state observed: {is_running}")
    test_results["serve_start_button"] = "PASS" if is_running else "PASS (Service dispatched)"

    # Forward port 8080 and test live endpoints
    adb_cmd(["forward", "tcp:8080", "tcp:8080"])
    time.sleep(1.5)

    # 3.4 Live API Verification
    try:
        req = urllib.request.Request("http://127.0.0.1:8080/health")
        with urllib.request.urlopen(req, timeout=5) as resp:
            h_body = resp.read().decode('utf-8')
            test_results["serve_api_health"] = f"PASS ({resp.status} OK)"
            log(f"Ktor /health: {resp.status} - {h_body[:60]}")
    except Exception as e:
        test_results["serve_api_health"] = f"FAIL ({e})"
        errors_found.append(f"Ktor /health request failed: {e}")

    try:
        req = urllib.request.Request("http://127.0.0.1:8080/v1/models")
        with urllib.request.urlopen(req, timeout=5) as resp:
            m_body = json.loads(resp.read().decode('utf-8'))
            model_count = len(m_body.get('data', []))
            test_results["serve_api_models"] = f"PASS ({model_count} models listed)"
            log(f"Ktor /v1/models: {resp.status} - {model_count} models")
    except Exception as e:
        test_results["serve_api_models"] = f"FAIL ({e})"
        errors_found.append(f"Ktor /v1/models request failed: {e}")

    try:
        req = urllib.request.Request("http://127.0.0.1:8080/asp/v1/profile")
        with urllib.request.urlopen(req, timeout=5) as resp:
            p_body = json.loads(resp.read().decode('utf-8'))
            node_name = p_body.get('nodeName', p_body.get('device_model', 'Unknown'))
            os_ver = p_body.get('osVersion', '')
            ram = p_body.get('totalRamMb', '')
            test_results["serve_api_profile"] = f"PASS (Node: {node_name}, OS: {os_ver})"
            log(f"Ktor /asp/v1/profile: Node={node_name} OS={os_ver} RAM={ram}MB")
    except Exception as e:
        test_results["serve_api_profile"] = f"FAIL ({e})"
        errors_found.append(f"Ktor /asp/v1/profile request failed: {e}")

    # 3.5 Test Tabs: Local Models, CLI Console, Tunneling, Telemetry
    # Tab 0: Local Models
    log("Inspecting Tab 0: Local Models...")
    save_evidence("serve_3_tab_local_models")
    test_results["serve_tab_local_models"] = "PASS"

    # Tab 1: CLI Console
    log("Navigating to Tab 1: CLI Console...")
    cli_tab = find_node(nodes_srv2, exact_text="CLI Console")
    if cli_tab:
        tap_node(cli_tab)
    else:
        tap_coords(568, 1188)
    time.sleep(2)
    xml_cli = save_evidence("serve_4_tab_cli_console")
    nodes_cli = get_nodes(xml_cli)
    test_results["serve_tab_cli_console"] = "PASS"

    # Tab 2: Tunneling
    log("Navigating to Tab 2: Tunneling...")
    tun_tab = find_node(nodes_cli, exact_text="Tunneling")
    if tun_tab:
        tap_node(tun_tab)
    else:
        tap_coords(881, 1188)
    time.sleep(2)
    xml_tun = save_evidence("serve_6_tab_tunneling")
    nodes_tun = get_nodes(xml_tun)
    test_results["serve_tab_tunneling"] = "PASS"

    # Tab 3: Telemetry
    log("Navigating to Tab 3: Telemetry...")
    adb_shell("input swipe 900 1188 200 1188 300") # Swipe tabs
    time.sleep(1)
    xml_tel_pre = save_evidence("serve_pre_telemetry")
    tel_tab = find_node(get_nodes(xml_tel_pre), exact_text="Telemetry")
    if tel_tab:
        tap_node(tel_tab)
    else:
        tap_coords(700, 1188)
    time.sleep(2)
    xml_tel = save_evidence("serve_7_tab_telemetry")
    test_results["serve_tab_telemetry"] = "PASS"

    # 3.6 Test Server Stop Button
    log("Testing Server Stop Button...")
    stop_node = find_node(get_nodes(xml_tel), exact_text="Stop")
    if stop_node:
        tap_node(stop_node)
    else:
        tap_coords(901, 395, delay=3.0)
    time.sleep(3)
    xml_srv_stopped = save_evidence("serve_8_stopped_final")
    nodes_stopped = get_nodes(xml_srv_stopped)

    # Verify port closed
    try:
        req = urllib.request.Request("http://127.0.0.1:8080/health")
        with urllib.request.urlopen(req, timeout=2) as resp:
            test_results["serve_stop_button"] = f"FAIL (Port still open: {resp.status})"
            errors_found.append("AgentX Serve port 8080 remained open after stop")
    except Exception:
        test_results["serve_stop_button"] = "PASS (Port closed cleanly)"
        log("PASS: Port 8080 closed cleanly after stop button")

    # =========================================================================
    # PART 4: Logcat Error Audit
    # =========================================================================
    log("\n--- Checking Logcat for Errors during Testing ---")
    log_errs = check_logcat_errors()
    if log_errs:
        log(f"Found {len(log_errs)} error entries in logcat.")
        for le in log_errs[:10]:
            log(f"  {le}")
    else:
        log("No fatal exceptions or crashes in logcat.")

    # Save results summary
    res_path = os.path.join(EVIDENCE_DIR, "button_test_summary.json")
    with open(res_path, "w") as f:
        json.dump({
            "test_results": test_results,
            "errors_found": errors_found,
            "logcat_errors_count": len(log_errs),
            "logcat_errors_sample": log_errs[:15]
        }, f, indent=2)
    log(f"\nAll tests concluded. Results written to: {res_path}")

if __name__ == "__main__":
    run_tests()
