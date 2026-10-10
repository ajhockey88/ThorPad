package com.thorpad;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.Display;

/** Invisible trampoline: opens the trackpad on the bottom screen, then disappears. */
public class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, TrackpadActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Display target = Displays.trackpadDisplay(this);
        try {
            if (target != null) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(target.getDisplayId());
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (RuntimeException e) {
            // The system refused to place us on that screen; open wherever we are instead.
            startActivity(intent);
        }
        finish();
    }
}
