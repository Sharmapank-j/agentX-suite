#!/usr/bin/env python3
import os
import re
import sys

def main():
    version = os.environ.get("GITHUB_REF_NAME", sys.argv[1] if len(sys.argv) > 1 else "").lstrip("v")
    changelog_path = "CHANGELOG.md"
    out_path = "RELEASE_BODY.md"

    if not os.path.exists(changelog_path):
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(f"AgentX Suite release v{version}\n")
        return

    with open(changelog_path, "r", encoding="utf-8") as f:
        text = f.read()

    pattern = rf"## \[{re.escape(version)}\][^\n]*\n(.*?)(?=\n## \[|\Z)"
    match = re.search(pattern, text, re.DOTALL)
    if match:
        body = match.group(1).strip()
    else:
        body = f"AgentX Suite release v{version}"

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(body + "\n")
    print(f"Extracted release notes for v{version} to {out_path}")

if __name__ == "__main__":
    main()
