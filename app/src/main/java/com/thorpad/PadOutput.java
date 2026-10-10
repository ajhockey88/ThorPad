package com.thorpad;

/** Where trackpad gestures go: a virtual mouse (Shizuku) or touch gestures (accessibility). */
interface PadOutput {
    void moveBy(float dx, float dy);
    void click();
    /** Press and hold on the pad without moving. */
    void longPress();
    /** Quick tap with two fingers. */
    void twoFingerTap();
    void dragBegin();
    void dragEnd();
    void scrollBegin();
    /** dy is in trackpad pixels, already scaled and signed for the user's scroll direction. */
    void scrollBy(float dy);
    void scrollEnd();
    void back();
    void home();
    void recents();
}
