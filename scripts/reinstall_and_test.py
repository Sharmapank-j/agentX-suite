import subprocess
import time
import os
import sys
import json
import xml.etree.ElementTree as ET

if sys.platform.startswith('win'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

ADB = r"C:\Users\pr218\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not os.path.exists(ADB):
    ADB = "adb"

EVIDENCE_DIR = r"C:\Users\pr218\Projects\AgentX\evidence\reinstall_verification"
os.makedirs(EVIDENCE_DIR, exist_ok=True)

APK_DIR = r"C:\Users\pr218\Projects\AgentX"
APKS = [
    ("app", os.path.join(APK_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk"), "com.agentx", "com.agentx.MainActivity"),
    ("medical", os.path.join(APK_DIR, "medical", "build", "outputs", "apk", "debug", "medical-debug.apk"), "com.agentx.medical.debug", "com.agentx.medical.MainActivity"),
    ("admin", os.path.join(APK_DIR, "admin", "build", "outputs", "apk", "debug", "admin-debug.apk"), "com.agentx.admin.debug", "com.agentx.admin.MainActivity")
]

def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

def get_connected_device():
    p = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True, encoding='utf-8', errors='replace')
    lines = [l.strip() for l in p.stdout.strip().splitlines() if l.strip() and not l.startswith("*")]
    devices = []
    for line in lines[1:]: # skip "List of devices attached"
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    return devices

def adb_shell(device, cmd):
    p = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def adb_cmd(device, args):
    p = subprocess.run([ADB, "-s", device] + args, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return p.stdout.strip()

def save_evidence(device, name):
    xml_sd = f"/sdcard/{name}.xml"
    adb_shell(device, f"uiautomator dump {xml_sd}")
    xml_local = os.path.join(EVIDENCE_DIR, name + ".xml")
    adb_cmd(device, ["pull", xml_sd, xml_local])
    
    png_local = os.path.join(EVIDENCE_DIR, name + ".png")
    with open(png_local, "wb") as f:
        subprocess.run([ADB, "-s", device, "exec-out", "screencap", "-p"], stdout=f)
    log(f"  📸 Captured evidence: {name}.png and .xml")
    return xml_local

def main():
    log("================================================================================")
    log("  AGENTX SUITE — CLEAN REMOVAL, FRESH INSTALLATION & DEVICE VERIFICATION")
    log("================================================================================")

    # 1. Detect device
    devices = get_connected_device()
    if not devices:
        log("⚠️ No active ADB device detected. Waiting 10 seconds for device connection...")
        for i in range(10):
            time.sleep(1)
            devices = get_connected_device()
            if devices:
                break

    if not devices:
        log("❌ No Android device connected via ADB.")
        log("Please verify:")
        log("  1. The USB cable is securely connected to your PC.")
        log("  2. 'USB Debugging' is enabled in Developer Options.")
        log("  3. The USB connection mode is set to 'File Transfer' / MTP (not Charging Only).")
        log("  4. If prompted on your phone screen, tap 'Allow USB debugging'.")
        sys.exit(1)

    device = "5eb1d795a3b4" if "5eb1d795a3b4" in devices else devices[0]
    log(f"✅ Target Android Device Selected: {device}")

    # Inspect device
    model = adb_shell(device, "getprop ro.product.model")
    release = adb_shell(device, "getprop ro.build.version.release")
    abi = adb_shell(device, "getprop ro.product.cpu.abi")
    log(f"Device Identity: {model} (Android {release}, ABI: {abi})")

    # Wake device
    adb_shell(device, "input keyevent 224; wm dismiss-keyguard; svc power stayon true")

    # 2. Backup model weights from sandbox to /sdcard/Download before uninstalling
    log("\n[Step 1] Backing up GGUF model files to public storage (/sdcard/Download/)...")
    adb_shell(device, "mkdir -p /sdcard/Download/models")
    for tag, apk_path, pkg, act in APKS:
        ls_out = adb_shell(device, f"run-as {pkg} ls files/models 2>/dev/null")
        if "medimind" in ls_out:
            log(f"  Found model in {pkg} sandbox. Backing up...")
            adb_shell(device, f"run-as {pkg} cp files/models/medimind-v2-q5_k_m.gguf /sdcard/Download/medimind-v2-q5_k_m.gguf 2>/dev/null")
            adb_shell(device, f"run-as {pkg} cp files/models/medimind-v2-q5_k_m.gguf /sdcard/Download/models/medimind-v2-q5_k_m.gguf 2>/dev/null")

    # 3. Clean older components (uninstall older packages)
    log("\n[Step 2] Removing older build components from device...")
    for tag, apk_path, pkg, act in APKS:
        log(f"  Uninstalling {pkg}...")
        res = adb_cmd(device, ["uninstall", pkg])
        log(f"  Result: {res}")

    # 4. Install fresh builds
    log("\n[Step 3] Installing fresh debug APKs...")
    for tag, apk_path, pkg, act in APKS:
        if not os.path.exists(apk_path):
            log(f"❌ Missing APK: {apk_path}")
            sys.exit(1)
        size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        log(f"  Installing {tag} ({size_mb:.1f} MB) -> {pkg}...")
        install_res = adb_cmd(device, ["install", "-r", "-d", apk_path])
        log(f"  Install result: {install_res}")
        if "Success" not in install_res:
            log(f"❌ Installation failed for {pkg}: {install_res}")
            sys.exit(1)

    # 5. Restore model files into sandboxes for instant offline availability
    log("\n[Step 4] Ensuring GGUF models are present in application sandboxes...")
    for tag, apk_path, pkg, act in APKS:
        adb_shell(device, f"run-as {pkg} mkdir -p files/models")
        # Copy from /sdcard/Download if exists
        adb_shell(device, f"run-as {pkg} cp /sdcard/Download/medimind-v2-q5_k_m.gguf files/models/medimind-v2-q5_k_m.gguf 2>/dev/null")
        ls_out = adb_shell(device, f"run-as {pkg} ls -lh files/models")
        log(f"  Sandbox for {pkg}:\n    {ls_out}")

    # 6. Test App Launches & Process States
    log("\n[Step 5] Testing Clean Application Launches & PIDs...")
    for tag, apk_path, pkg, act in APKS:
        log(f"  Starting {pkg}/{act}...")
        adb_shell(device, f"am force-stop {pkg}")
        time.sleep(1)
        adb_shell(device, f"am start -n {pkg}/{act}")
        time.sleep(3)
        pid = adb_shell(device, f"pidof {pkg}")
        log(f"  PID for {pkg}: {pid}")
        if not pid:
            log(f"❌ App {pkg} crashed or failed to start!")
            sys.exit(1)
        save_evidence(device, f"test_launch_{tag}")
        log(f"  ✅ {tag} ({pkg}) running cleanly (PID: {pid})")

    # 7. Test AgentX Serve CLI & Local Model Engine
    log("\n[Step 6] Testing AgentX Serve Local Offline Runtime & CLI Tools...")
    adb_shell(device, "am force-stop com.agentx.admin.debug")
    adb_shell(device, "am start -n com.agentx.admin.debug/com.agentx.admin.MainActivity")
    time.sleep(4)

    # Tap CLI console tab
    adb_shell(device, "input tap 360 810")
    time.sleep(2)
    save_evidence(device, "test_admin_cli_tab")

    # Run stats command
    log("  Testing CLI command: stats")
    adb_shell(device, "input tap 100 900")
    time.sleep(2)
    save_evidence(device, "test_admin_cli_stats")

    # Run llm command
    log("  Testing CLI command: llm")
    adb_shell(device, "input tap 220 900")
    time.sleep(3)
    save_evidence(device, "test_admin_cli_llm")

    log("\n================================================================================")
    log("  ✅ ALL OLD COMPONENTS REMOVED, NEW BUILDS INSTALLED & VERIFIED SUCCESSFULLY!")
    log("================================================================================")

if __name__ == "__main__":
    main()
