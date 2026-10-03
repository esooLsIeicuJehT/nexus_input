#!/system/bin/sh

PACKAGE=com.inputmapper.platform
ACTIVITY=com.inputmapper.platform.ui.MainActivity

if pm path "$PACKAGE" >/dev/null 2>&1; then
    am start -n "$PACKAGE/$ACTIVITY" >/dev/null 2>&1
else
    echo "NEXUS INPUT APK ($PACKAGE) is not installed."
    exit 1
fi
