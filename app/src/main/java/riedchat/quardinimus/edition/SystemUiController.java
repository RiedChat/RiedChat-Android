package riedchat.quardinimus.edition;

import android.app.Activity;
import android.os.Build;
import android.view.View;

/** Иммерсивный полноэкранный режим (скрытие статус-бара и навигации). */
public final class SystemUiController {

    private SystemUiController() {}

    @SuppressWarnings("deprecation")
    public static void hideSystemUI(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(
                    android.view.WindowInsets.Type.statusBars() |
                    android.view.WindowInsets.Type.navigationBars()
                );
                controller.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
}
