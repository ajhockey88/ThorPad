package com.thorpad;

/**
 * Extra things a key output can say about the text box it types into. Only Android text boxes
 * (through ThorPad's Android keyboard) know them; games and the virtual USB keyboard don't.
 */
interface TextContext {
    /** Whether the next letter should be a capital (start of a sentence, a name field...). */
    boolean capitalizeNext();

    /** False for passwords, emails, web addresses and number boxes: type exactly what's pressed. */
    boolean allowsCorrections();

    /** Whether the text box is a password box. */
    boolean isPassword();

    /** Up to n characters before the cursor, or null if unknown. */
    CharSequence textBefore(int n);
}
