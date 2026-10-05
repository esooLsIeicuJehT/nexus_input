package com.inputmapper.platform.shizuku;

interface IShizukuInputService {
    String injectTouch(
        int action,
        long downTime,
        long eventTime,
        int pointerCount,
        in int[] pointerIds,
        in float[] xs,
        in float[] ys
    ) = 1;

    String injectKey(
        int keyCode,
        int action,
        long downTime,
        long eventTime,
        int metaState,
        int repeatCount
    ) = 2;

    String selfTest() = 3;

    String readSurfaceLayers() = 4;
    String readSurfaceLatency(String layer) = 5;

    void destroy() = 16777114;
}
