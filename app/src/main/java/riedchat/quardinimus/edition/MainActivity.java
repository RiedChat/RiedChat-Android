package riedchat.quardinimus.edition;

import androidx.fragment.app.FragmentActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

public class MainActivity extends FragmentActivity {

    private WebView webView;
    private FrameLayout rootLayout;
    private FrameLayout fullscreenContainer;

    private ChatWebChromeClient webChromeClient;
    private DownloadManagerHelper downloadManagerHelper;

    // Устанавливается из JS через CallStateBridge при каждом изменении
    // S.call в call-manager.js. См. javadoc CallStateBridge и onPause() ниже.
    private volatile boolean callActive = false;

    private static final String CHAT_URL = "https://riedchat.github.io/RiedChat/";

    // true между onResume() и onPause() - используется, чтобы понять, можно
    // ли сразу грузить страницу заново после гибели renderer-процесса, или
    // отложить до следующего onResume() (см. onRenderProcessGone ниже).
    private boolean activityResumed = false;
    private volatile boolean rendererReloadPending = false;

    public void setCallActive(boolean active) {
        callActive = active;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Root layout holds the WebView plus a container used for fullscreen HTML5 video
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        rootLayout.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootLayout);

        SystemUiController.hideSystemUI(this);

        setupWebView();
        webView.loadUrl(CHAT_URL);
    }

    // Создаёт WebView и все мосты/обработчики к нему. Вынесено из onCreate(),
    // чтобы этот же код можно было повторно прогнать в
    // recreateWebViewAfterRendererGone(), когда старый WebView уже мёртв
    // (renderer-процесс убит системой) и его нельзя переиспользовать -
    // нужен полностью новый экземпляр WebView.
    private void setupWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setFitsSystemWindows(false);
        rootLayout.addView(webView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new KeyboardInsetHandler(rootLayout, webView).attach();

        WebViewConfigurator.configure(webView, this);

        webView.setWebViewClient(new TrustedHostWebViewClient(this));

        webChromeClient = new ChatWebChromeClient(this, webView, fullscreenContainer);
        webView.setWebChromeClient(webChromeClient);

        // Мост AES-256-GCM ключа в AndroidKeyStore для riedchat/src/crypto/vault.js
        // (режим 'android-native') — см. VaultKeystoreBridge.java. Ключ никогда
        // не покидает Keystore, наружу в JS идут только plaintext/ciphertext.
        webView.addJavascriptInterface(new VaultKeystoreBridge(this, webView), "AndroidVaultBridge");

        // См. CallStateBridge — сообщает MainActivity, идёт ли звонок сейчас,
        // чтобы onPause() не сбрасывал AudioManager.MODE_IN_COMMUNICATION
        // посреди звонка (диалог разрешений, сворачивание и т.п.).
        webView.addJavascriptInterface(new CallStateBridge(this), "AndroidCallBridge");

        // Настраивает AndroidDownloadInterface + DownloadListener внутри себя.
        downloadManagerHelper = new DownloadManagerHelper(this, webView);
    }

    // Вызывается из TrustedHostWebViewClient.onRenderProcessGone(). Старый
    // WebView после гибели renderer-процесса непригоден (даже reload() на
    // нём обычно не восстанавливает страницу), поэтому убираем его из
    // дерева, уничтожаем и поднимаем новый с нуля. Если Activity сейчас не
    // видима (пользователь ещё в фоне/недавних) - откладываем loadUrl до
    // onResume(), чтобы не грузить тяжёлую SPA-страницу впустую под капотом.
    void recreateWebViewAfterRendererGone() {
        runOnUiThread(() -> {
            try {
                rootLayout.removeView(webView);
                webView.destroy();
            } catch (Exception ignored) {}

            setupWebView();

            if (activityResumed) {
                webView.loadUrl(CHAT_URL);
            } else {
                rendererReloadPending = true;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        downloadManagerHelper.onRequestPermissionsResult(requestCode, grantResults);
        webChromeClient.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SystemUiController.hideSystemUI(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        webView.onResume();
        webView.resumeTimers();
        SystemUiController.hideSystemUI(this);

        // Renderer был убит, пока Activity была в фоне (см.
        // recreateWebViewAfterRendererGone) - страницу тогда не грузили,
        // догружаем её только теперь, когда есть смысл рендерить.
        if (rendererReloadPending) {
            rendererReloadPending = false;
            webView.loadUrl(CHAT_URL);
            return;
        }

        // document.visibilitychange в WebView не гарантированно срабатывает
        // при сворачивании/разворачивании Activity (WebView остаётся attached
        // к окну), поэтому проверку живости соединения триггерим явно отсюда,
        // а не полагаемся только на JS-события (см. app.js __androidResume).
        webView.evaluateJavascript(
            "(function(){try{if(window.__androidResume){window.__androidResume();}}catch(e){}})();",
            null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        // Сбрасываем режим аудио, который ChatWebChromeClient переключает в
        // MODE_IN_COMMUNICATION перед выдачей доступа к микрофону (см. её
        // javadoc — без этого WebView падает с NotReadableError). Если не
        // сбрасывать, MODE_IN_COMMUNICATION остаётся навсегда и обычное
        // воспроизведение звука/видео в чате может звучать тише или уйти
        // не в тот аудио-канал (как при звонке), а не как обычное медиа.
        //
        // БАГ (исправлено): раньше сброс был безусловным — onPause()
        // срабатывает и на доли секунды, когда поверх Activity всплывает
        // системный диалог запроса разрешений CAMERA/RECORD_AUDIO, то есть
        // ровно в момент, когда WebView только получает доступ к микрофону
        // для звонка. Режим откатывался в MODE_NORMAL прямо во время
        // getUserMedia, тот падал с NotReadableError, call-manager.js ловил
        // это и на video+audio, и на audio-only фоллбэке — localStream
        // оставался null, клиент входил в звонок receive-only и не отдавал
        // в PeerConnection ни видео, ни аудио треков, хотя само соединение
        // (ICE/DTLS) устанавливалось нормально и медиа собеседника
        // продолжало приходить. Теперь не трогаем режим, пока callActive
        // (см. CallStateBridge) — во время звонка сворачивание/диалоги не
        // должны сбрасывать аудио-режим.
        if (callActive) {
            return;
        }
        android.media.AudioManager audioManager =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // БАГ: webView.destroy() вызывался немедленно и обрывал WebSocket
        // ДО того, как JS успевал штатно отключиться (Strophe disconnect() ->
        // отправка presence unavailable + закрытие XMPP-потока). Сервер видел
        // не аккуратное закрытие сессии, а внезапный обрыв — и mod_smacks
        // (см. prosody_cfg.lua) держал сессию в состоянии resumption какое-то
        // время, прежде чем разослать контактам unavailable. Поэтому статус
        // "online" не пропадал сразу при выходе из приложения, хотя в обычном
        // браузере закрытие вкладки работает штатно (браузер успевает
        // отправить закрывающую стансу до реального разрыва сокета).
        //
        // ИСПРАВЛЕНИЕ: сначала просим JS аккуратно отключиться, и только
        // через небольшую паузу (достаточную, чтобы close-стансу успел уйти
        // по сети) уничтожаем сам WebView.
        try {
            webView.evaluateJavascript(
                "(function(){try{if(window.App&&App.state&&App.state.connection){App.state.connection.disconnect();}}catch(e){}})();",
                null);
        } catch (Exception ignored) {}

        webView.postDelayed(() -> {
            try { webView.destroy(); } catch (Exception ignored) {}
        }, 400);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        webChromeClient.handleFileChooserResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        SystemUiController.hideSystemUI(this);
    }

    @Override
    public void onBackPressed() {
        if (webChromeClient.hasCustomView()) {
            webChromeClient.hideCustomViewIfShown();
            return;
        }
        // SPA не пользуется history.pushState для переключения между списком
        // чатов и открытым чатом (см. app.js openChat / back-btn), поэтому
        // webView.canGoBack() тут всегда false и системный "назад" сразу
        // закрывал бы приложение. Вместо этого спрашиваем сам сайт через
        // window.__androidHandleBack (см. app.js): он сам решает, закрыть ли
        // открытую модалку или вернуться из чата в список, и возвращает
        // true/false — обработал ли он нажатие сам.
        webView.evaluateJavascript(
            "(function(){try{return !!(window.__androidHandleBack && window.__androidHandleBack());}catch(e){return false;}})();",
            value -> {
                if (!"true".equals(value)) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        MainActivity.super.onBackPressed();
                    }
                }
            });
    }
}
