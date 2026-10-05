#!/system/bin/sh
# Root-module controls only. APK assets do not include this module.

nexus_number() { case "$1" in ''|*[!0-9]*) return 1;; esac; }
nexus_governor() { case "$1" in ''|*[!a-zA-Z0-9_-]*) return 1;; esac; }
nexus_member() { for nexus_item in $2; do [ "$nexus_item" = "$1" ] && return 0; done; return 1; }

nexus_read() {
    if [ -r "$1" ]; then cat "$1" || return 1; else echo "UNAVAILABLE: $1 is not readable"; return 1; fi
}

nexus_write_checked() {
    nexus_path=$1 nexus_value=$2
    [ -r "$nexus_path" ] && [ -w "$nexus_path" ] || { echo "ERROR node is not readable/writable: $nexus_path"; return 1; }
    nexus_old=$(cat "$nexus_path") || return 1
    if ! printf '%s\n' "$nexus_value" > "$nexus_path"; then echo "ERROR write rejected: $nexus_path"; return 1; fi
    nexus_after=$(cat "$nexus_path") || nexus_after=UNREADABLE
    if [ "$nexus_after" != "$nexus_value" ]; then
        echo "ERROR read-back differs: requested=$nexus_value observed=$nexus_after"
        if printf '%s\n' "$nexus_old" > "$nexus_path" && [ "$(cat "$nexus_path")" = "$nexus_old" ]; then echo "ROLLBACK_VERIFIED=$nexus_old";
        else echo "ERROR rollback could not be verified; inspect $nexus_path"; fi
        return 1
    fi
    echo "APPLIED=$nexus_path"
    echo "BEFORE=$nexus_old"
    echo "READ_BACK=$nexus_after"
}

nexus_cpu_governor() {
    nexus_number "$1" && nexus_governor "$2" || { echo 'ERROR invalid CPU policy/governor'; return 1; }
    nexus_dir="/sys/devices/system/cpu/cpufreq/policy$1"
    nexus_available=$(nexus_read "$nexus_dir/scaling_available_governors") || { echo "$nexus_available"; return 1; }
    nexus_member "$2" "$nexus_available" || { echo 'ERROR governor is not exposed by this CPU policy'; return 1; }
    nexus_write_checked "$nexus_dir/scaling_governor" "$2"
}

nexus_cpu_frequencies() {
    nexus_number "$1" && nexus_number "$2" && nexus_number "$3" || { echo 'ERROR invalid CPU policy/frequencies'; return 1; }
    [ "$2" -le "$3" ] || { echo 'ERROR minimum exceeds maximum'; return 1; }
    nexus_dir="/sys/devices/system/cpu/cpufreq/policy$1"
    nexus_available=$(nexus_read "$nexus_dir/scaling_available_frequencies") || { echo "$nexus_available"; echo 'ERROR no verified discrete frequency table'; return 1; }
    nexus_member "$2" "$nexus_available" && nexus_member "$3" "$nexus_available" || { echo 'ERROR frequency is absent from actual kernel table'; return 1; }
    nexus_failed=false
    nexus_min=$(cat "$nexus_dir/scaling_min_freq") || return 1
    nexus_max=$(cat "$nexus_dir/scaling_max_freq") || return 1
    # Expand the old range first so a valid new interval is not rejected by current bounds.
    if [ "$3" -gt "$nexus_max" ]; then
        nexus_write_checked "$nexus_dir/scaling_max_freq" "$3" || return 1
        nexus_write_checked "$nexus_dir/scaling_min_freq" "$2" || nexus_failed=true
    else
        nexus_write_checked "$nexus_dir/scaling_min_freq" "$2" || return 1
        nexus_write_checked "$nexus_dir/scaling_max_freq" "$3" || nexus_failed=true
    fi
    if [ "${nexus_failed:-false}" = true ]; then
        nexus_current_min=$(cat "$nexus_dir/scaling_min_freq")
        if [ "$nexus_current_min" -gt "$nexus_max" ]; then
            printf '%s\n' "$nexus_min" > "$nexus_dir/scaling_min_freq"
            printf '%s\n' "$nexus_max" > "$nexus_dir/scaling_max_freq"
        else
            printf '%s\n' "$nexus_max" > "$nexus_dir/scaling_max_freq"
            printf '%s\n' "$nexus_min" > "$nexus_dir/scaling_min_freq"
        fi
        if [ "$(cat "$nexus_dir/scaling_min_freq")" = "$nexus_min" ] && [ "$(cat "$nexus_dir/scaling_max_freq")" = "$nexus_max" ]; then echo 'ROLLBACK_VERIFIED=CPU_INTERVAL';
        else echo 'ERROR CPU interval rollback could not be verified'; fi
        return 1
    fi
    [ "$(cat "$nexus_dir/scaling_min_freq")" = "$2" ] && [ "$(cat "$nexus_dir/scaling_max_freq")" = "$3" ] || { echo 'ERROR final CPU interval read-back changed'; return 1; }
    echo "CPU_INTERVAL_READ_BACK=$2 $3"
}

nexus_gpu_governor() {
    # devfreq IDs are real sysfs basenames, never caller-supplied absolute paths.
    case "$1" in ''|.|..|*[!a-zA-Z0-9_.:-]*) echo 'ERROR invalid devfreq ID'; return 1;; esac
    nexus_governor "$2" || { echo 'ERROR invalid devfreq governor'; return 1; }
    nexus_dir="/sys/class/devfreq/$1"
    nexus_available=$(nexus_read "$nexus_dir/available_governors") || { echo "$nexus_available"; return 1; }
    nexus_member "$2" "$nexus_available" || { echo 'ERROR governor is not exposed by this devfreq device'; return 1; }
    nexus_write_checked "$nexus_dir/governor" "$2"
}

nexus_swappiness() {
    nexus_number "$1" && [ "$1" -le 100 ] || { echo 'ERROR swappiness must be 0..100'; return 1; }
    nexus_write_checked /proc/sys/vm/swappiness "$1"
}
