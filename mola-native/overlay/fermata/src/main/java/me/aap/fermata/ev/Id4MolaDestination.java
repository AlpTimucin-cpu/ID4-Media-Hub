package me.aap.fermata.ev;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.NavigationCoordinator;

/**
 * Trusted in-package, user-initiated phone navigation from the ID.4 cockpit.
 * This does not create media streams, bypass DRM, enable car video, or replace
 * the existing Mola add-ons. Unknown destinations fail closed to Dashboard.
 */
public final class Id4MolaDestination {
    public static final String EXTRA = "id4_media_target";

    private Id4MolaDestination() {}

    /** Only literal IDs sent by our JavaScriptInterface may reach this method. */
    public static int fragmentFor(String target) {
        if (target == null) return R.id.dashboard_fragment;
        switch (target) {
            case "tv": return R.id.tv_fragment;
            case "youtube": return R.id.youtube_fragment;
            case "radio": return R.id.radio_fragment;
            case "podcast": return R.id.podcast_fragment;
            case "media": return R.id.folders_fragment;
            case "web": return R.id.web_browser_fragment;
            case "settings":
            case "equalizer": return R.id.settings_fragment;
            // Internal Mola dashboard actions stay on the original dashboard.
            case "mola":
            case "mirror":
            case "windasist":
            case "shortcuts": return R.id.dashboard_fragment;
            default: return R.id.dashboard_fragment;
        }
    }

    public static void navigate(MainActivityDelegate delegate, String target) {
        if (delegate == null) return;
        int fragment = fragmentFor(target);
        try {
            if (!NavigationCoordinator.select(delegate, fragment)) {
                delegate.returnToDashboard();
            }
        } catch (RuntimeException e) {
            // A missing/disabled add-on should never terminate the original
            // media dashboard. Navigation is a convenience, not playback.
            delegate.returnToDashboard();
        }
    }
}
