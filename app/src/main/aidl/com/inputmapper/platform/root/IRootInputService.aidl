package com.inputmapper.platform.root;

interface IRootInputService {
    String create(int width, int height, int maxSlots);
    String touchDown(int slot, int trackingId, int x, int y);
    String touchMove(int slot, int x, int y);
    String touchUp(int slot);
    String key(int linuxKeyCode, int value);
    String status();
    String publishState(String state);
    String destroyDevices();
    String readSurfaceLayers();
    String readSurfaceLatency(String layer);
}
