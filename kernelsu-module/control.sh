#!/system/bin/sh
MODDIR=${0%/*}
STATE_DIR=/data/adb/gamepad-pro
. "$MODDIR/control-lib.sh"
[ "$(id -u)" = 0 ] || { echo 'ERROR root UID is required'; exit 1; }

capabilities() {
    echo 'CPU_BEGIN'
    for policy in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$policy" ] || continue
        echo "POLICY=${policy##*policy}"
        for field in related_cpus scaling_governor scaling_available_governors scaling_cur_freq scaling_min_freq scaling_max_freq scaling_available_frequencies; do
            value=$(nexus_read "$policy/$field"); echo "$field=$value"
        done
    done
    echo 'CPU_END'
    echo 'DEVFREQ_BEGIN'
    for device in /sys/class/devfreq/*; do
        [ -d "$device" ] || continue
        echo "DEVFREQ=${device##*/}"
        for field in name governor available_governors cur_freq min_freq max_freq available_frequencies; do
            value=$(nexus_read "$device/$field"); echo "$field=$value"
        done
    done
    echo 'DEVFREQ_END'
    echo 'THERMAL_BEGIN'
    for zone in /sys/class/thermal/thermal_zone*; do
        [ -d "$zone" ] || continue
        echo "${zone##*/}: type=$(nexus_read "$zone/type") raw_temp=$(nexus_read "$zone/temp")"
    done
    echo 'Thermal configuration is read-only: no device-specific writable adapter has been verified.'
    echo 'THERMAL_END'
    echo 'MEMORY_BEGIN'
    nexus_read /proc/meminfo
    nexus_read /proc/swaps
    for zram in /sys/block/zram*; do
        [ -d "$zram" ] || continue
        echo "${zram##*/}: disksize=$(nexus_read "$zram/disksize") mm_stat=$(nexus_read "$zram/mm_stat")"
    done
    echo "swappiness=$(nexus_read /proc/sys/vm/swappiness)"
    echo 'Active ZRAM resizing and compaction are unsupported until a device-specific recovery path is verified.'
    echo 'MEMORY_END'
    echo 'BATTERY_BEGIN'
    dumpsys battery || echo 'ERROR battery service unavailable'
    echo 'BATTERY_END'
    echo 'PRESETS_BEGIN'
    echo 'No hardware-validated frequency presets are installed. Use exposed device controls with read-back verification.'
    echo 'PRESETS_END'
}

safeguards() {
    battery=$(dumpsys battery) || { echo 'ERROR battery safeguards unavailable'; return 1; }
    level=$(printf '%s\n' "$battery" | sed -n 's/^ *level: *//p')
    temperature=$(printf '%s\n' "$battery" | sed -n 's/^ *temperature: *//p')
    nexus_number "$level" && nexus_number "$temperature" || { echo 'ERROR battery level/temperature was not provided by Android'; return 1; }
    [ "$level" -ge 20 ] && [ "$temperature" -lt 450 ] || { echo "ERROR safeguard refused tuning: battery=$level%, temperature=$temperature tenths C"; return 1; }
    echo "SAFEGUARD_BATTERY=$level"
    echo "SAFEGUARD_TEMPERATURE=$temperature"
}

case "${1:-capabilities}" in
    capabilities) capabilities; exit $?;;
    logs) nexus_read "$STATE_DIR/control.log"; exit $?;;
    cpu-governor|cpu-frequencies|devfreq-governor|swappiness) ;;
    *) echo 'ERROR unknown or unsupported root control'; exit 1;;
esac
mkdir -p "$STATE_DIR" || exit 1
chmod 0700 "$STATE_DIR" || exit 1
mkdir "$STATE_DIR/control.lock" 2>/dev/null || { echo 'ERROR another root change is active, or a previous change was interrupted; inspect logs'; exit 1; }
trap 'rmdir "$STATE_DIR/control.lock"' EXIT
trap 'echo "ERROR root change interrupted; read current device values before retrying"; exit 130' HUP INT TERM
safeguards || exit 1
operation=$1
shift
(
    echo "timestamp=$(date '+%Y-%m-%dT%H:%M:%S%z') operation=$operation arguments=$*"
    case "$operation" in
        cpu-governor) [ "$#" = 2 ] && nexus_cpu_governor "$@";;
        cpu-frequencies) [ "$#" = 3 ] && nexus_cpu_frequencies "$@";;
        devfreq-governor) [ "$#" = 2 ] && nexus_gpu_governor "$@";;
        swappiness) [ "$#" = 1 ] && nexus_swappiness "$@";;
    esac
    result=$?
    echo "CONTROL_EXIT=$result"
    exit "$result"
) > "$STATE_DIR/control-result.txt" 2>&1
result=$?
cat "$STATE_DIR/control-result.txt" >> "$STATE_DIR/control.log"
chmod 0600 "$STATE_DIR/control.log" "$STATE_DIR/control-result.txt"
cat "$STATE_DIR/control-result.txt"
exit "$result"
