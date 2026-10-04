package com.inputmapper.platform.root;

/** JNI boundary only. No privilege/backend policy belongs here. */
public final class NativeUinputBridge {
    private NativeUinputBridge() {}

    public static native int nativeCreate(int width, int height, int maxSlots);
    public static native int nativeTouchDown(int slot, int trackingId, int x, int y);
    public static native int nativeTouchMove(int slot, int x, int y);
    public static native int nativeTouchUp(int slot);
    public static native int nativeKey(int linuxKeyCode, int value);
    public static native void nativeDestroy();
    public static native String nativeLastError();
}
