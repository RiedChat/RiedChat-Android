package riedchat.quardinimus.edition;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Отвечает за fullscreen HTML5 <video>, file chooser (в т.ч. множественный
 * выбор файлов) и запросы доступа к камере/микрофону (getUserMedia).
 *
 * PermissionRequest от WebView — это отдельный уровень поверх обычных ОС-
 * разрешений: даже если request.grant() вызван, getUserMedia в странице
 * всё равно упадёт, если у приложения нет самого разрешения CAMERA/
 * RECORD_AUDIO на уровне ОС. Поэтому перед grant() сначала проверяем и при
 * необходимости запрашиваем недостающие runtime-разрешения, и только потом
 * подтверждаем запрос WebView (см. onRequestPermissionsResult).
 */
public class ChatWebChromeClient extends WebChromeClient {

    private static final String TAG = "ChatWebChromeClient";
    public static final int FILE_CHOOSER_REQUEST = 1;
    public static final int MEDIA_PERMISSION_REQUEST = 3;

    private final Activity activity;
    private final WebView webView;
    private final FrameLayout fullscreenContainer;

    private View customView;
    private CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingMediaRequest;

    public ChatWebChromeClient(Activity activity, WebView webView, FrameLayout fullscreenContainer) {
        this.activity = activity;
        this.webView = webView;
        this.fullscreenContainer = fullscreenContainer;
    }

    @Override
    public boolean onShowFileChooser(WebView webView,
            ValueCallback<Uri[]> filePathCallback,
            FileChooserParams fileChooserParams) {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
        }
        fileChooserCallback = filePathCallback;
        Intent intent = fileChooserParams.createIntent();
        // Multiple file selection for websites
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            activity.startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } catch (Exception e) {
            fileChooserCallback = null;
            return false;
        }
        return true;
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) {
        String[] osPermissions = mapToOsPermissions(request.getResources());
        android.util.Log.d(TAG, "onPermissionRequest: origin=" + request.getOrigin()
                + " resources=" + java.util.Arrays.toString(request.getResources())
                + " osPermissions=" + java.util.Arrays.toString(osPermissions));
        if (osPermissions.length == 0) {
            // Запрошенные ресурсы не требуют CAMERA/RECORD_AUDIO (например,
            // PROTECTED_MEDIA_ID) — подтверждаем сразу.
            request.grant(request.getResources());
            return;
        }

        boolean allGranted = true;
        for (String permission : osPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            android.util.Log.d(TAG, "onPermissionRequest: OS permissions already granted, grant() immediately");
            prepareAudioIfNeeded(osPermissions);
            request.grant(request.getResources());
            return;
        }

        if (pendingMediaRequest != null) {
            pendingMediaRequest.deny();
        }
        pendingMediaRequest = request;
        android.util.Log.d(TAG, "onPermissionRequest: requesting OS permissions " + java.util.Arrays.toString(osPermissions));
        ActivityCompat.requestPermissions(activity, osPermissions, MEDIA_PERMISSION_REQUEST);
    }

    /** Вызывается из Activity#onRequestPermissionsResult. */
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != MEDIA_PERMISSION_REQUEST || pendingMediaRequest == null) {
            return;
        }
        boolean allGranted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        android.util.Log.d(TAG, "onRequestPermissionsResult: grantResults=" + java.util.Arrays.toString(grantResults)
                + " allGranted=" + allGranted);
        if (allGranted) {
            prepareAudioIfNeeded(mapToOsPermissions(pendingMediaRequest.getResources()));
            pendingMediaRequest.grant(pendingMediaRequest.getResources());
            android.util.Log.d(TAG, "onRequestPermissionsResult: PermissionRequest.grant() called");
        } else {
            String[] requestedPermissions = mapToOsPermissions(pendingMediaRequest.getResources());
            pendingMediaRequest.deny();
            // Если разрешение уже было один раз отклонено, ОС больше не
            // показывает системный диалог: requestPermissions() сразу
            // возвращает DENIED без какого-либо UI, и с точки зрения
            // пользователя выглядит так, будто приложение вообще не
            // спрашивает доступ, а сайт молча пишет "нет доступа". В этом
            // случае ведём в настройки приложения, где разрешение можно
            // включить вручную.
            boolean permanentlyDenied = false;
            for (String permission : requestedPermissions) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    permanentlyDenied = true;
                    break;
                }
            }
            if (permanentlyDenied) {
                promptOpenAppSettings();
            }
        }
        pendingMediaRequest = null;
    }

    private void promptOpenAppSettings() {
        android.widget.Toast.makeText(activity,
                "Доступ к камере/микрофону отключён. Включите его в настройках приложения",
                android.widget.Toast.LENGTH_LONG).show();
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        try {
            activity.startActivity(intent);
        } catch (Exception ignored) {}
    }

    /**
     * Явно переводит аудио-подсистему Android в MODE_IN_COMMUNICATION перед
     * тем, как WebView получит доступ к микрофону. Без этого шага, даже при
     * выданном RECORD_AUDIO и подтверждённом PermissionRequest.grant(),
     * getUserMedia в WebView падает с NotReadableError: "Could not start
     * audio source" — Chromium внутри WebView не запрашивает audio focus
     * сам, эту работу должно сделать хост-приложение (см. MODIFY_AUDIO_SETTINGS
     * в манифесте).
     */
    private void prepareAudioIfNeeded(String[] osPermissions) {
        for (String permission : osPermissions) {
            if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
                AudioManager audioManager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                }
                return;
            }
        }
    }

    private static String[] mapToOsPermissions(String[] webResources) {
        boolean needsCamera = false;
        boolean needsMic = false;
        for (String resource : webResources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) needsCamera = true;
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) needsMic = true;
        }
        if (needsCamera && needsMic) {
            return new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        } else if (needsCamera) {
            return new String[]{Manifest.permission.CAMERA};
        } else if (needsMic) {
            return new String[]{Manifest.permission.RECORD_AUDIO};
        }
        return new String[0];
    }

    // --- Fullscreen support for the default HTML5 <video> player ---
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;

        webView.setVisibility(View.GONE);
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.bringToFront();

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        SystemUiController.hideSystemUI(activity);
    }

    @Override
    public void onHideCustomView() {
        if (customView == null) {
            return;
        }
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenContainer.removeView(customView);
        webView.setVisibility(View.VISIBLE);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }
        customView = null;
        customViewCallback = null;

        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        SystemUiController.hideSystemUI(activity);
    }

    public boolean hasCustomView() {
        return customView != null;
    }

    public void hideCustomViewIfShown() {
        if (customView != null) {
            onHideCustomView();
        }
    }

    /** Вызывается из Activity#onActivityResult. */
    public void handleFileChooserResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileChooserCallback.onReceiveValue(results);
        fileChooserCallback = null;
    }
}
