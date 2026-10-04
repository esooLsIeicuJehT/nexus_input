package com.inputmapper.platform.root

import android.view.KeyEvent

/** Android KeyEvent -> Linux input-event-code mapping. Native code receives only Linux codes. */
object LinuxKeyCodeMapper {
    fun fromAndroid(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> 1
        KeyEvent.KEYCODE_1 -> 2
        KeyEvent.KEYCODE_2 -> 3
        KeyEvent.KEYCODE_3 -> 4
        KeyEvent.KEYCODE_4 -> 5
        KeyEvent.KEYCODE_5 -> 6
        KeyEvent.KEYCODE_6 -> 7
        KeyEvent.KEYCODE_7 -> 8
        KeyEvent.KEYCODE_8 -> 9
        KeyEvent.KEYCODE_9 -> 10
        KeyEvent.KEYCODE_0 -> 11
        KeyEvent.KEYCODE_DEL -> 14
        KeyEvent.KEYCODE_TAB -> 15
        KeyEvent.KEYCODE_Q -> 16
        KeyEvent.KEYCODE_W -> 17
        KeyEvent.KEYCODE_E -> 18
        KeyEvent.KEYCODE_R -> 19
        KeyEvent.KEYCODE_T -> 20
        KeyEvent.KEYCODE_Y -> 21
        KeyEvent.KEYCODE_U -> 22
        KeyEvent.KEYCODE_I -> 23
        KeyEvent.KEYCODE_O -> 24
        KeyEvent.KEYCODE_P -> 25
        KeyEvent.KEYCODE_ENTER -> 28
        KeyEvent.KEYCODE_A -> 30
        KeyEvent.KEYCODE_S -> 31
        KeyEvent.KEYCODE_D -> 32
        KeyEvent.KEYCODE_F -> 33
        KeyEvent.KEYCODE_G -> 34
        KeyEvent.KEYCODE_H -> 35
        KeyEvent.KEYCODE_J -> 36
        KeyEvent.KEYCODE_K -> 37
        KeyEvent.KEYCODE_L -> 38
        KeyEvent.KEYCODE_Z -> 44
        KeyEvent.KEYCODE_X -> 45
        KeyEvent.KEYCODE_C -> 46
        KeyEvent.KEYCODE_V -> 47
        KeyEvent.KEYCODE_B -> 48
        KeyEvent.KEYCODE_N -> 49
        KeyEvent.KEYCODE_M -> 50
        KeyEvent.KEYCODE_SPACE -> 57
        KeyEvent.KEYCODE_DPAD_UP -> 103
        KeyEvent.KEYCODE_DPAD_DOWN -> 108
        KeyEvent.KEYCODE_DPAD_LEFT -> 105
        KeyEvent.KEYCODE_DPAD_RIGHT -> 106
        KeyEvent.KEYCODE_HOME -> 102
        KeyEvent.KEYCODE_MOVE_END -> 107
        KeyEvent.KEYCODE_PAGE_UP -> 104
        KeyEvent.KEYCODE_PAGE_DOWN -> 109
        KeyEvent.KEYCODE_VOLUME_DOWN -> 114
        KeyEvent.KEYCODE_VOLUME_UP -> 115
        KeyEvent.KEYCODE_BACK -> 158
        KeyEvent.KEYCODE_MENU -> 139
        KeyEvent.KEYCODE_BUTTON_A -> 304
        KeyEvent.KEYCODE_BUTTON_B -> 305
        KeyEvent.KEYCODE_BUTTON_X -> 307
        KeyEvent.KEYCODE_BUTTON_Y -> 308
        KeyEvent.KEYCODE_BUTTON_L1 -> 310
        KeyEvent.KEYCODE_BUTTON_R1 -> 311
        KeyEvent.KEYCODE_BUTTON_SELECT -> 314
        KeyEvent.KEYCODE_BUTTON_START -> 315
        KeyEvent.KEYCODE_BUTTON_MODE -> 316
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 317
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 318
        else -> null
    }
}
