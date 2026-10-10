package com.thorpad;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Display;

/** Invisible trampoline: opens the trackpad on the bottom screen, then disappears. */
public class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openTrackpad(this, false);
        finish();
    }

    /** Opens the trackpad on the bottom screen, with its keyboard showing if asked. */
    static void openTrackpad(Context context, boolean withKeyboard) {
        Intent intent = new Intent(context, TrackpadActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (withKeyboard) intent.putExtra(TrackpadActivity.EXTRA_OPEN_KEYBOARD, true);
        Display target = Displays.trackpadDisplay(context);
        try {
            if (target != null) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(target.getDisplayId());
                context.startActivity(intent, options.toBundle());
            } else {
                context.startActivity(intent);
            }
        } catch (RuntimeException e) {
            // The system refused to place us on that screen; open wherever we are instead.
            try {
                context.startActivity(intent);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
