#!/usr/bin/env python3
from pathlib import Path
import sys

cpp = Path(__file__).parents[1] / "app/src/main/cpp/uinput_jni.cpp"
text = cpp.read_text()
forbidden = [
    "KernelSU", "Magisk", "Shizuku", "PackageManager", "Accessibility",
    "su -c", "getprop", "android.permission", "backend"
]
hits = [token for token in forbidden if token in text]
if hits:
    print("Native boundary violation:", ", ".join(hits))
    sys.exit(1)
required = ["/dev/uinput", "UI_DEV_CREATE", "ABS_MT_SLOT", "ABS_MT_TRACKING_ID", "EV_KEY"]
missing = [token for token in required if token not in text]
if missing:
    print("Native implementation missing expected raw-uinput primitives:", ", ".join(missing))
    sys.exit(1)
print("OK: native file contains raw uinput operations only; no privilege/backend policy tokens found.")
