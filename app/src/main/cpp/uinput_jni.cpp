#include <jni.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>

namespace {
pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;
int g_touch_fd = -1;
int g_keyboard_fd = -1;
int g_width = 0;
int g_height = 0;
int g_slot_count = 0;
bool g_slots[32] = {};
char g_last_error[256] = "";

void set_error_text(const char* text) {
    if (text == nullptr) text = "unknown error";
    snprintf(g_last_error, sizeof(g_last_error), "%s", text);
}

int fail(const char* operation) {
    const int err = errno == 0 ? EIO : errno;
    snprintf(g_last_error, sizeof(g_last_error), "%s: %s", operation, strerror(err));
    return -err;
}

int fail_code(int code, const char* message) {
    set_error_text(message);
    return -code;
}

bool emit_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    const ssize_t written = write(fd, &ev, sizeof(ev));
    return written == (ssize_t)sizeof(ev);
}

bool sync_fd(int fd) {
    return emit_event(fd, EV_SYN, SYN_REPORT, 0);
}

bool setup_abs_axis(int fd, unsigned int code, int minimum, int maximum) {
    if (ioctl(fd, UI_SET_ABSBIT, code) < 0) return false;
    struct uinput_abs_setup setup;
    memset(&setup, 0, sizeof(setup));
    setup.code = (__u16)code;
    setup.absinfo.minimum = minimum;
    setup.absinfo.maximum = maximum;
    setup.absinfo.fuzz = 0;
    setup.absinfo.flat = 0;
    setup.absinfo.resolution = 0;
    return ioctl(fd, UI_ABS_SETUP, &setup) == 0;
}

void destroy_fd(int* fd) {
    if (fd != nullptr && *fd >= 0) {
        ioctl(*fd, UI_DEV_DESTROY);
        close(*fd);
        *fd = -1;
    }
}

int create_touchscreen(int width, int height, int max_slots) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return fail("open touchscreen /dev/uinput");

    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0 || ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH) < 0) {
        close(fd);
        return fail("configure touchscreen key capabilities");
    }
    if (ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0 || ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT) < 0) {
        close(fd);
        return fail("configure touchscreen abs/direct capabilities");
    }

    if (!setup_abs_axis(fd, ABS_X, 0, width - 1) ||
        !setup_abs_axis(fd, ABS_Y, 0, height - 1) ||
        !setup_abs_axis(fd, ABS_MT_SLOT, 0, max_slots - 1) ||
        !setup_abs_axis(fd, ABS_MT_TRACKING_ID, 0, 65535) ||
        !setup_abs_axis(fd, ABS_MT_POSITION_X, 0, width - 1) ||
        !setup_abs_axis(fd, ABS_MT_POSITION_Y, 0, height - 1)) {
        close(fd);
        return fail("configure touchscreen absolute axes");
    }

    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    strncpy(setup.name, "InputMapper Virtual Touchscreen", UINPUT_MAX_NAME_SIZE - 1);
    setup.name[UINPUT_MAX_NAME_SIZE - 1] = '\0';
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = 0x18D1;
    setup.id.product = 0x4EE7;
    setup.id.version = 1;
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
        close(fd);
        return fail("create virtual touchscreen");
    }
    g_touch_fd = fd;
    return 0;
}

int create_keyboard() {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return fail("open keyboard /dev/uinput");
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) {
        close(fd);
        return fail("configure keyboard EV_KEY");
    }
    for (int key = 0; key <= KEY_MAX; ++key) {
        if (ioctl(fd, UI_SET_KEYBIT, key) < 0) {
            close(fd);
            return fail("configure keyboard key capabilities");
        }
    }

    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    strncpy(setup.name, "InputMapper Virtual Keyboard", UINPUT_MAX_NAME_SIZE - 1);
    setup.name[UINPUT_MAX_NAME_SIZE - 1] = '\0';
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = 0x18D1;
    setup.id.product = 0x4EE8;
    setup.id.version = 1;
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0 || ioctl(fd, UI_DEV_CREATE) < 0) {
        close(fd);
        return fail("create virtual keyboard");
    }
    g_keyboard_fd = fd;
    return 0;
}

bool valid_point(int x, int y) {
    return x >= 0 && y >= 0 && x < g_width && y < g_height;
}

bool any_slot_active() {
    for (int i = 0; i < g_slot_count; ++i) {
        if (g_slots[i]) return true;
    }
    return false;
}

void clear_slots() {
    memset(g_slots, 0, sizeof(g_slots));
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeCreate(
        JNIEnv*, jclass, jint width, jint height, jint max_slots) {
    pthread_mutex_lock(&g_mutex);

    int result = 0;
    if (g_touch_fd >= 0 || g_keyboard_fd >= 0) {
        result = fail_code(EALREADY, "uinput devices already created");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (width <= 0 || height <= 0 || max_slots <= 0 || max_slots > 32) {
        result = fail_code(EINVAL, "invalid width/height/maxSlots");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    g_width = width;
    g_height = height;
    g_slot_count = max_slots;
    clear_slots();

    result = create_touchscreen(width, height, max_slots);
    if (result == 0) {
        result = create_keyboard();
        if (result != 0) destroy_fd(&g_touch_fd);
    }

    if (result == 0) g_last_error[0] = '\0';
    pthread_mutex_unlock(&g_mutex);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeTouchDown(
        JNIEnv*, jclass, jint slot, jint tracking_id, jint x, jint y) {
    pthread_mutex_lock(&g_mutex);

    if (g_touch_fd < 0) {
        int result = fail_code(ENODEV, "virtual touchscreen is not created");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (slot < 0 || slot >= g_slot_count || !valid_point(x, y)) {
        int result = fail_code(EINVAL, "invalid touch-down arguments");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (g_slots[slot]) {
        int result = fail_code(EBUSY, "touch slot is already active");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    const bool first = !any_slot_active();
    if (!emit_event(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_TRACKING_ID, tracking_id) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_POSITION_X, x) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_POSITION_Y, y) ||
        (first && !emit_event(g_touch_fd, EV_KEY, BTN_TOUCH, 1)) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_X, x) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_Y, y) ||
        !sync_fd(g_touch_fd)) {
        int result = fail("write touch-down event");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    g_slots[slot] = true;
    pthread_mutex_unlock(&g_mutex);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeTouchMove(
        JNIEnv*, jclass, jint slot, jint x, jint y) {
    pthread_mutex_lock(&g_mutex);

    if (g_touch_fd < 0) {
        int result = fail_code(ENODEV, "virtual touchscreen is not created");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (slot < 0 || slot >= g_slot_count || !g_slots[slot] || !valid_point(x, y)) {
        int result = fail_code(EINVAL, "invalid touch-move arguments or inactive slot");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    if (!emit_event(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_POSITION_X, x) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_POSITION_Y, y) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_X, x) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_Y, y) ||
        !sync_fd(g_touch_fd)) {
        int result = fail("write touch-move event");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    pthread_mutex_unlock(&g_mutex);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeTouchUp(
        JNIEnv*, jclass, jint slot) {
    pthread_mutex_lock(&g_mutex);

    if (g_touch_fd < 0) {
        int result = fail_code(ENODEV, "virtual touchscreen is not created");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (slot < 0 || slot >= g_slot_count || !g_slots[slot]) {
        int result = fail_code(EINVAL, "invalid touch-up slot or slot is inactive");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    g_slots[slot] = false;
    const bool last = !any_slot_active();
    if (!emit_event(g_touch_fd, EV_ABS, ABS_MT_SLOT, slot) ||
        !emit_event(g_touch_fd, EV_ABS, ABS_MT_TRACKING_ID, -1) ||
        (last && !emit_event(g_touch_fd, EV_KEY, BTN_TOUCH, 0)) ||
        !sync_fd(g_touch_fd)) {
        int result = fail("write touch-up event");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    pthread_mutex_unlock(&g_mutex);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeKey(
        JNIEnv*, jclass, jint linux_key_code, jint value) {
    pthread_mutex_lock(&g_mutex);

    if (g_keyboard_fd < 0) {
        int result = fail_code(ENODEV, "virtual keyboard is not created");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }
    if (linux_key_code < 0 || linux_key_code > KEY_MAX || (value != 0 && value != 1 && value != 2)) {
        int result = fail_code(EINVAL, "invalid Linux key code/value");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    if (!emit_event(g_keyboard_fd, EV_KEY, (__u16)linux_key_code, value) || !sync_fd(g_keyboard_fd)) {
        int result = fail("write keyboard event");
        pthread_mutex_unlock(&g_mutex);
        return result;
    }

    pthread_mutex_unlock(&g_mutex);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeDestroy(JNIEnv*, jclass) {
    pthread_mutex_lock(&g_mutex);
    destroy_fd(&g_keyboard_fd);
    destroy_fd(&g_touch_fd);
    clear_slots();
    g_slot_count = 0;
    g_width = 0;
    g_height = 0;
    pthread_mutex_unlock(&g_mutex);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_inputmapper_platform_root_NativeUinputBridge_nativeLastError(JNIEnv* env, jclass) {
    pthread_mutex_lock(&g_mutex);
    char copy[sizeof(g_last_error)];
    memcpy(copy, g_last_error, sizeof(copy));
    copy[sizeof(copy) - 1] = '\0';
    pthread_mutex_unlock(&g_mutex);
    return env->NewStringUTF(copy);
}
