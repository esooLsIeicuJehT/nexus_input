#!/system/bin/sh

STATE_DIR=/data/adb/gamepad-pro
STATUS_FILE="$STATE_DIR/boot-status.txt"
mkdir -p "$STATE_DIR"
chmod 0700 "$STATE_DIR"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 3
done

{
    echo "timestamp=$(date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || date)"
    echo "model=$(getprop ro.product.model)"
    echo "device=$(getprop ro.product.device)"
    echo "android=$(getprop ro.build.version.release)"
    echo "sdk=$(getprop ro.build.version.sdk)"
    echo "kernel=$(uname -r)"
    echo "selinux=$(getenforce 2>/dev/null || echo unknown)"
    if [ -e /dev/uinput ]; then
        echo "uinput=present"
        ls -lZ /dev/uinput 2>&1 | sed 's/^/uinput_ls=/'
    else
        echo "uinput=missing"
    fi
    if [ -e /dev/uhid ]; then
        echo "uhid=present"
        ls -lZ /dev/uhid 2>&1 | sed 's/^/uhid_ls=/'
    else
        echo "uhid=missing"
    fi
    echo "input_devices_begin"
    for event in /sys/class/input/event*; do
        [ -d "$event" ] || continue
        name=$(cat "$event/device/name" 2>/dev/null)
        vendor=$(cat "$event/device/id/vendor" 2>/dev/null)
        product=$(cat "$event/device/id/product" 2>/dev/null)
        echo "$(basename "$event")|$vendor:$product|$name"
    done
    echo "input_devices_end"
} > "$STATUS_FILE" 2>&1
chmod 0600 "$STATUS_FILE"
