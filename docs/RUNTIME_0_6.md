# Runtime 0.6 Notes

## In-game failure found from 0.5 device testing

The overlay editor ran above the target game, but `onAccessibilityEvent()` discarded target-package events while the editor existed. Pressing Play closed the overlay and armed the profile, but Android was not required to emit a second window-state event. The runtime could therefore remain armed with `runtimeProfile == null`, and controller capture/injection never activated.

A second issue was geometry lifetime: arming from the portrait NEXUS Activity could create the root virtual touchscreen using portrait width/height, then reuse it after the game switched to landscape. Mapping coordinates are normalized against the live game window, so a portrait-created uinput device is not a valid output target for landscape coordinates.

0.6 tracks the foreground package while editing, explicitly activates the edited profile when Play is pressed, and recreates the injector whenever live display geometry changes.

## D-pad

Some controllers expose the D-pad primarily as joystick HAT axes instead of Android key events. 0.6 converts HAT transitions into Android DPAD key identities. These synthesized events travel through the same persistent binding model as real key events.

## Control plane

A small `TYPE_ACCESSIBILITY_OVERLAY` NEXUS bubble remains available while mapping is armed. It is a control-plane UI only. High-rate input/output remains in the Accessibility capture + Binder/JNI data path.
