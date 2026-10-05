#!/system/bin/sh

PACKAGE=com.inputmapper.platform
ACTIVITY=com.example.MainActivity
COMPONENT="$PACKAGE/$ACTIVITY"

if pm path "$PACKAGE" >/dev/null 2>&1; then
    if ! am start -n "$COMPONENT" >/dev/null 2>&1; then
        echo "NEXUS INPUT APK is installed, but Android could not launch $COMPONENT."
        exit 2
    fi
else
    echo "NEXUS INPUT APK ($PACKAGE) is not installed."
    exit 1
fi
