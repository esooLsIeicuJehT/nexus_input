#!/system/bin/sh

ui_print "- NEXUS INPUT Root Companion"
ui_print "- KernelSU: ${KSU_VER:-unknown} (${KSU_VER_CODE:-unknown})"
ui_print "- Device ABI: ${ARCH:-unknown}; Android API: ${API:-unknown}"

if [ "${KSU:-false}" != "true" ]; then
    abort "This package is a KernelSU module and must be installed from KernelSU Manager."
fi

if [ -e /dev/uinput ]; then
    ui_print "- /dev/uinput: present"
else
    ui_print "- /dev/uinput: NOT present (root touch backend will report this explicitly)"
fi

if [ -e /dev/uhid ]; then
    ui_print "- /dev/uhid: present (future physical-HID output path available for validation)"
else
    ui_print "- /dev/uhid: not present"
fi

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/update.sh" 0 0 0755
