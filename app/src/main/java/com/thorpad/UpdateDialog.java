package com.thorpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The "ThorPad x is available" dialog, from Settings or from the check when ThorPad opens. */
final class UpdateDialog {
    private UpdateDialog() {}

    /** "ThorPad x is available": the release notes, with Update and Not now. */
    static void show(final Activity activity, final Updater.Release r, final Updater updater,
            final DialogInterface.OnClickListener notNow) {
        float d = activity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * d);
        box.setPadding(pad, (int) (18 * d), pad, 0);

        TextView title = new TextView(activity);
        final boolean same = r.version.equals(r.current);
        title.setText(same ? "🔁 Reinstall ThorPad " + r.version + "?" : "🎉 ThorPad " + r.version + " is available");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        TextView sub = new TextView(activity);
        sub.setText((same ? "Downloads this version again and installs it over the one you have. Settings are kept"
                : "You have " + r.current) + (r.apkSize > 0 ? " · " + (r.apkSize / 1024) + " KB download" : ""));
        sub.setTextSize(13);
        sub.setTextColor(Color.rgb(0x8a, 0x90, 0x9b));
        sub.setPadding(0, (int) (4 * d), 0, (int) (10 * d));
        box.addView(sub);

        // Long notes scroll, so the buttons always stay on screen.
        final int maxH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.55f);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        TextView notes = new TextView(activity);
        notes.setText(ReleaseNotes.format(r.notes));
        notes.setTextSize(14);
        notes.setTextColor(Color.rgb(0xc9, 0xce, 0xd8));
        notes.setLineSpacing(0, 1.15f);
        scroll.addView(notes);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setView(box)
                .setNegativeButton("Not now", notNow)
                .setPositiveButton(same ? "Reinstall" : "Update", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { updater.install(r); }
                })
                .show();
    }
}
