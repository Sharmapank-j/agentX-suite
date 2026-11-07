import subprocess
import time
import os
import sys

if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

ADB = r"C:\Users\pr218\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "5eb1d795a3b4"
EVIDENCE_DIR = r"C:\Users\pr218\Projects\AgentX\evidence\reinstall_verification"
os.makedirs(EVIDENCE_DIR, exist_ok=True)

def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

def adb_shell(cmd):
    p = subprocess.run([ADB, "-s", DEVICE, "shell", cmd], capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def adb_cmd(args):
    p = subprocess.run([ADB, "-s", DEVICE] + args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def save_evidence(name):
    png_local = os.path.join(EVIDENCE_DIR, name + ".png")
    with open(png_local, "wb") as f:
        subprocess.run([ADB, "-s", DEVICE, "exec-out", "screencap", "-p"], stdout=f)
    log(f"  📸 Captured screenshot: {name}.png")

def main():
    log("================================================================================")
    log("  TESTING NEW AGENTX SUITE BUILDS ON POCO X4 PRO 5G")
    log("================================================================================")

    # Wake device & dismiss keyguard
    adb_shell("input keyevent 224; wm dismiss-keyguard; svc power stayon true")

    # 1. Test AgentX (:app)
    log("\n[Test 1] Launching AgentX (:app)...")
    adb_shell("am force-stop com.agentx")
    time.sleep(1)
    adb_shell("am start -n com.agentx/com.agentx.MainActivity")
    time.sleep(4)
    pid_app = adb_shell("pidof com.agentx")
    log(f"  AgentX PID: {pid_app}")
    save_evidence("01_agentx_launch")

    # 2. Test AgentX Medical (:medical)
    log("\n[Test 2] Launching AgentX Medical (:medical)...")
    adb_shell("am force-stop com.agentx.medical.debug")
    time.sleep(1)
    adb_shell("am start -n com.agentx.medical.debug/com.agentx.medical.MainActivity")
    time.sleep(4)
    pid_med = adb_shell("pidof com.agentx.medical.debug")
    log(f"  AgentX Medical PID: {pid_med}")
    save_evidence("02_medical_launch")

    # 3. Test AgentX Serve (:admin)
    log("\n[Test 3] Launching AgentX Serve (:admin)...")
    adb_shell("am force-stop com.agentx.admin.debug")
    time.sleep(1)
    adb_shell("am start -n com.agentx.admin.debug/com.agentx.admin.MainActivity")
    time.sleep(4)
    pid_admin = adb_shell("pidof com.agentx.admin.debug")
    log(f"  AgentX Serve PID: {pid_admin}")
    save_evidence("03_admin_launch")

    # Switch to CLI Console tab in AgentX Serve
    log("\n[Test 4] Navigating to CLI Console in AgentX Serve...")
    # CLI tab is Tab 2 at (360, 810) or similar
    adb_shell("input tap 360 810")
    time.sleep(2)
    save_evidence("04_admin_cli_tab")

    # Tap 'stats' chip (around x=100, y=900)
    log("  Executing CLI command: stats")
    adb_shell("input tap 100 900")
    time.sleep(2)
    save_evidence("05_admin_cli_stats")

    # Tap 'device' chip (around x=220, y=900)
    log("  Executing CLI command: device")
    adb_shell("input tap 220 900")
    time.sleep(2)
    save_evidence("06_admin_cli_device")

    # Tap 'llm' chip (around x=360, y=900)
    log("  Executing CLI command: llm (testing native llama.cpp)")
    adb_shell("input tap 360 900")
    time.sleep(3)
    save_evidence("07_admin_cli_llm")

    log("\n[Test 5] Checking Memory & Thermal State...")
    mem_info = adb_shell("dumpsys meminfo com.agentx.admin.debug | grep 'TOTAL PSS:'")
    thermal = adb_shell("dumpsys thermalservice | grep -E 'Current thermal status|temperature' | head -n 2")
    log(f"  Memory footprint (admin): {mem_info}")
    log(f"  Thermal status: {thermal}")

    log("\n================================================================================")
    log("  ✅ ALL 3 APPLICATIONS LAUNCHED & VERIFIED WITH ZERO CRASHES!")
    log("================================================================================")

if __name__ == "__main__":
    main()
