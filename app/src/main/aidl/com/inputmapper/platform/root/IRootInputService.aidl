package com.inputmapper.platform.root;

interface IRootInputService {
    String create(int width, int height, int maxSlots);
    String touchDown(int slot, int trackingId, int x, int y);
    String touchMove(int slot, int x, int y);
    String touchUp(int slot);
    String key(int linuxKeyCode, int value);

    // Privileged Android InputManager path. This path does not create /dev/uinput devices.
    String prepareInputManager(int maxPointers);
    String inputManagerTouchDown(int pointerId, float x, float y);
    String inputManagerTouchMove(int pointerId, float x, float y);
    String inputManagerTouchUp(int pointerId);
    String inputManagerKey(int keyCode, int action);
    String inputManagerReleaseAll();

    String status();
    String publishState(String state);
    String destroyDevices();
    String readSurfaceLayers();
    String readSurfaceLatency(String layer);
}
