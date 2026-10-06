package android.hardware.input;

import android.view.InputEvent;

/** Minimal Binder contract used by the root-side injector. */
interface IInputManager {
    boolean injectInputEvent(in InputEvent event, int mode);
}
