package com.thorpad;

/** Where the on-screen keyboard's keys go. */
interface KeyOutput {
    /** Special keys that aren't characters. */
    int ENTER = 1, BACKSPACE = 2, TAB = 3, ESCAPE = 4, LEFT = 5, RIGHT = 6, UP = 7, DOWN = 8;
    /** Deletes all the text in the selected text box. */
    int CLEAR_ALL = 9;

    void typeChar(char c);
    void special(int key);
}
