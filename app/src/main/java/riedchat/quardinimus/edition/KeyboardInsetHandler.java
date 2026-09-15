package riedchat.quardinimus.edition;

import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

/**
 * Because the activity draws edge-to-edge (setDecorFitsSystemWindows(false)
 * in SystemUiController.hideSystemUI()), Android will NOT automatically
 * resize our content when the keyboard opens — android:windowSoftInputMode=
 * "adjustResize" alone only affects windows that still let the system handle
 * inset fitting. With adjustPan (the old setting) the window is panned
 * instead of resized, so the WebView's own pixel size never changes and the
 * page inside it never sees a viewport resize — that's what produced the big
 * empty gap above the keyboard in the screenshot: the composer/messages
 * layout stayed sized for the pre-keyboard viewport while the whole window
 * shifted up underneath it.
 *
 * First attempt here just added bottom PADDING to the WebView on IME inset
 * changes — that had no visible effect, because Android WebView's Chromium
 * engine computes its layout viewport (and therefore CSS `100dvh` /
 * `visualViewport`, see chat/js/features/viewport.js) from the view's actual
 * measured BOUNDS, not from padding. Padding just insets where content is
 * drawn inside the same-sized view; it does not shrink the viewport the page
 * thinks it has, so nothing downstream ever fired.
 *
 * This version instead changes the WebView's real height via its
 * LayoutParams whenever the IME inset changes, so the WebView is physically
 * shorter while the keyboard is open. That's a genuine bounds change, which
 * Chromium does account for, so the page's own keyboard-aware layout
 * (visualViewport resize / 100dvh) kicks in and the composer ends up sitting
 * right above the keyboard with no gap. The WindowInsetsAnimationCompat
 * callback keeps it in sync frame-by-frame with the keyboard's open/close
 * animation instead of just snapping.
 */
public class KeyboardInsetHandler {

    private final FrameLayout rootLayout;
    private final WebView webView;

    public KeyboardInsetHandler(FrameLayout rootLayout, WebView webView) {
        this.rootLayout = rootLayout;
        this.webView = webView;
    }

    public void attach() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            applyImeInsetToWebView(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            return insets;
        });

        ViewCompat.setWindowInsetsAnimationCallback(rootLayout,
                new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                    @Override
                    public WindowInsetsCompat onProgress(WindowInsetsCompat insets,
                            List<WindowInsetsAnimationCompat> runningAnimations) {
                        applyImeInsetToWebView(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                        return insets;
                    }
                });
    }

    private void applyImeInsetToWebView(int imeBottomPx) {
        ViewGroup.LayoutParams lp = webView.getLayoutParams();
        if (imeBottomPx <= 0) {
            if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                webView.setLayoutParams(lp);
            }
            return;
        }
        int totalHeight = rootLayout.getHeight();
        if (totalHeight <= 0) {
            // Not laid out yet; nothing to compute against.
            return;
        }
        int targetHeight = Math.max(0, totalHeight - imeBottomPx);
        if (lp.height != targetHeight) {
            lp.height = targetHeight;
            webView.setLayoutParams(lp);
        }
    }
}
