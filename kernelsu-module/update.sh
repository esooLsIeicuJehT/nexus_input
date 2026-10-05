#!/system/bin/sh

MODDIR=${0%/*}
STATE_DIR=/data/adb/gamepad-pro
STATUS_FILE="$STATE_DIR/update-status.txt"
UPDATE_JSON_URL="https://raw.githubusercontent.com/esooLsIeicuJehT/nexus_input/main/update.json"
TMP_DIR="$STATE_DIR/update"
TMP_JSON="$TMP_DIR/update.json"
TMP_ZIP="$TMP_DIR/module.zip"

mkdir -p "$STATE_DIR" "$TMP_DIR" || {
    echo "ERROR=unable to create update state directory"
    exit 1
}
chmod 0700 "$STATE_DIR" "$TMP_DIR" 2>/dev/null || true

. "$MODDIR/update-lib.sh" || { echo "STATE=ERROR";echo "MESSAGE=Updater verification library is missing";exit 1; }

CURRENT_CODE=$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)
CURRENT_VERSION=$(sed -n 's/^version=//p' "$MODDIR/module.prop" | head -n 1)
[ -n "$CURRENT_CODE" ] || {
    echo "ERROR=unable to read current versionCode"
    exit 1
}

find_busybox() {
    for candidate in /data/adb/ksu/bin/busybox /data/adb/ksu/bin/ksu_busybox "$(command -v busybox 2>/dev/null)"; do
        [ -n "$candidate" ] || continue
        [ -x "$candidate" ] || continue
        if "$candidate" --list 2>/dev/null | grep -qx wget; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

json_string() {
    key=$1
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$TMP_JSON" | head -n 1
}

json_number() {
    key=$1
    sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*[,}].*/\1/p" "$TMP_JSON" | head -n 1
}

write_status() {
    {
        echo "timestamp=$(date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null || date)"
        echo "currentVersion=$CURRENT_VERSION"
        echo "currentVersionCode=$CURRENT_CODE"
        for line in "$@"; do
            echo "$line"
        done
    } > "$STATUS_FILE"
    chmod 0600 "$STATUS_FILE" 2>/dev/null || true
}

check_update() {
    if ! fetch_file "$UPDATE_JSON_URL" "$TMP_JSON"; then
        write_status "state=error" "message=Failed to download update.json"
        echo "STATE=ERROR"
        echo "MESSAGE=Failed to download update.json from GitHub"
        return 2
    fi

    REMOTE_VERSION=$(json_string version)
    REMOTE_CODE=$(json_number versionCode)
    ZIP_URL=$(json_string zipUrl)
    SHA256=$(json_string sha256)
    CHANGELOG=$(json_string changelog)

    if ! validate_update "$REMOTE_VERSION" "$REMOTE_CODE" "$ZIP_URL" "$SHA256"; then
        write_status "state=error" "message=Malformed update.json"
        echo "STATE=ERROR"
        echo "MESSAGE=Malformed update.json"
        return 3
    fi

    echo "CURRENT_VERSION=$CURRENT_VERSION"
    echo "CURRENT_VERSION_CODE=$CURRENT_CODE"
    echo "REMOTE_VERSION=$REMOTE_VERSION"
    echo "REMOTE_VERSION_CODE=$REMOTE_CODE"
    echo "ZIP_URL=$ZIP_URL"
    echo "SHA256=$SHA256"
    echo "CHANGELOG=$CHANGELOG"

    if [ "$REMOTE_CODE" -gt "$CURRENT_CODE" ] 2>/dev/null; then
        write_status "state=available" "remoteVersion=$REMOTE_VERSION" "remoteVersionCode=$REMOTE_CODE" "zipUrl=$ZIP_URL"
        echo "STATE=AVAILABLE"
        return 10
    fi

    if [ "$REMOTE_CODE" -lt "$CURRENT_CODE" ] 2>/dev/null; then
        write_status "state=local_newer" "remoteVersion=$REMOTE_VERSION" "remoteVersionCode=$REMOTE_CODE" \
            "message=Published update channel is older than the installed module"
        echo "STATE=LOCAL_NEWER"
        echo "MESSAGE=Published update channel is older than installed $CURRENT_VERSION; no downgrade will be offered"
        return 0
    fi

    write_status "state=up_to_date" "remoteVersion=$REMOTE_VERSION" "remoteVersionCode=$REMOTE_CODE"
    echo "STATE=UP_TO_DATE"
    return 0
}

find_ksud() {
    for candidate in "$(command -v ksud 2>/dev/null)" /data/adb/ksu/bin/ksud /data/adb/ksud; do
        [ -n "$candidate" ] || continue
        [ -x "$candidate" ] || continue
        echo "$candidate"
        return 0
    done
    return 1
}

install_update() {
    check_update
    CHECK_RC=$?
    if [ "$CHECK_RC" -eq 0 ]; then
        STATE=$(json_string state 2>/dev/null || true)
        echo "MESSAGE=No newer published update is available"
        return 0
    fi
    [ "$CHECK_RC" -eq 10 ] || return "$CHECK_RC"

    REMOTE_VERSION=$(json_string version)
    REMOTE_CODE=$(json_number versionCode)
    ZIP_URL=$(json_string zipUrl)
    SHA256=$(json_string sha256)

    echo "MESSAGE=Downloading $REMOTE_VERSION"
    if ! fetch_file "$ZIP_URL" "$TMP_ZIP"; then
        write_status "state=error" "message=Failed to download module ZIP" "remoteVersion=$REMOTE_VERSION"
        echo "STATE=ERROR"
        echo "MESSAGE=Failed to download module ZIP"
        return 4
    fi

    if ! verify_sha256 "$SHA256" "$TMP_ZIP"; then
        rm -f "$TMP_ZIP"
        write_status "state=error" "message=Module ZIP SHA-256 verification failed" "remoteVersion=$REMOTE_VERSION"
        echo "STATE=ERROR"
        echo "MESSAGE=Module ZIP SHA-256 verification failed"
        return 5
    fi

    KSUD=$(find_ksud 2>/dev/null || true)
    if [ -z "$KSUD" ]; then
        write_status "state=error" "message=ksud executable not found" "remoteVersion=$REMOTE_VERSION"
        echo "STATE=ERROR"
        echo "MESSAGE=ksud executable not found"
        return 6
    fi

    echo "MESSAGE=Installing with $KSUD module install"
    if "$KSUD" module install "$TMP_ZIP"; then
        write_status "state=installed_pending_reboot" "remoteVersion=$REMOTE_VERSION" "remoteVersionCode=$REMOTE_CODE"
        echo "STATE=INSTALLED_PENDING_REBOOT"
        echo "MESSAGE=Update installed. Reboot to activate $REMOTE_VERSION"
        return 0
    fi

    write_status "state=error" "message=ksud module install failed" "remoteVersion=$REMOTE_VERSION"
    echo "STATE=ERROR"
    echo "MESSAGE=ksud module install failed"
    return 7
}

case "${1:-check}" in
    check)
        check_update
        ;;
    install)
        install_update
        ;;
    status)
        if [ -r "$STATUS_FILE" ]; then cat "$STATUS_FILE"; else echo "state=never_checked"; fi
        ;;
    *)
        echo "Usage: $0 {check|install|status}"
        exit 64
        ;;
esac
