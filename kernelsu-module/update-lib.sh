#!/system/bin/sh
# Single source of update transport and mandatory integrity verification.

validate_update() {
    version=$1
    code=$2
    url=$3
    digest=$4
    printf '%s\n' "$version" | grep -Eq '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$' || return 1
    printf '%s\n' "$code" | grep -Eq '^[1-9][0-9]{0,8}$' || return 1
    printf '%s\n' "$digest" | grep -Eq '^[a-f0-9]{64}$' || return 1
    # Preserve the existing published raw asset path and accept this repository's release assets only.
    printf '%s\n' "$url" | grep -Eq '^https://(github\.com/esooLsIeicuJehT/nexus_input/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\.zip|raw\.githubusercontent\.com/esooLsIeicuJehT/nexus_input/main/releases/[A-Za-z0-9._-]+\.zip)$' || return 1
}

fetch_file() {
    url=$1
    dest=$2
    rm -f "$dest"

    BB=$(find_busybox 2>/dev/null || true)
    if [ -n "$BB" ]; then
        echo "MESSAGE=Fetching through BusyBox wget" >&2
        "$BB" wget -q -O "$dest" "$url" && [ -s "$dest" ] && return 0
    fi

    CURL=$(command -v curl 2>/dev/null || true)
    if [ -n "$CURL" ]; then
        echo "MESSAGE=Fetching through curl after earlier transport was unavailable or failed" >&2
        "$CURL" --proto '=https' --proto-redir '=https' -fL --connect-timeout 15 --max-time 120 -o "$dest" "$url" \
            && [ -s "$dest" ] && return 0
    fi

    WGET=$(command -v wget 2>/dev/null || true)
    if [ -n "$WGET" ]; then
        echo "MESSAGE=Fetching through wget after earlier transports were unavailable or failed" >&2
        "$WGET" -q -O "$dest" "$url" && [ -s "$dest" ] && return 0
    fi

    return 1
}

verify_sha256() {
    expected=$1
    file=$2
    printf '%s\n' "$expected" | grep -Eq '^[a-f0-9]{64}$' || {
        echo "ERROR=Required SHA-256 digest is missing or malformed"
        return 1
    }

    BB=$(find_busybox 2>/dev/null || true)
    if [ -n "$BB" ] && "$BB" --list 2>/dev/null | grep -qx sha256sum; then
        actual=$("$BB" sha256sum "$file" | awk '{print $1}')
    elif command -v sha256sum >/dev/null 2>&1; then
        actual=$(sha256sum "$file" | awk '{print $1}')
    else
        echo "ERROR=No SHA-256 tool is available"
        return 2
    fi

    [ "$actual" = "$expected" ] || {
        echo "ERROR=SHA256 mismatch expected=$expected actual=$actual"
        return 1
    }
}

