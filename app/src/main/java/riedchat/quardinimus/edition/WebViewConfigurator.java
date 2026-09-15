package riedchat.quardinimus.edition;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Базовые настройки WebView: JS/DOM storage, кэш, User-Agent, куки. */
public final class WebViewConfigurator {

    public static final String UA_ANDROID =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240105.004) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.6778.200 Mobile Safari/537.36";

    private WebViewConfigurator() {}

    public static void configure(WebView webView, Context context) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);

        // БАГ (исправлено): раньше тут стоял LOAD_NO_CACHE + clearCache(true) на
        // каждом запуске — WebView вообще не пользовался диск-кэшем и каждый
        // раз стирал накопленное. Тогда сервер не слал строгих Cache-Control
        // на статику, и это был единственный надёжный способ не залипать на
        // старой версии. Теперь nginx отдаёт immutable/max-age на хэшированные
        // assets/*.js|css и no-cache на index.html (см. server-конфиг) — этого
        // достаточно, чтобы штатный LOAD_DEFAULT работал правильно: index.html
        // всегда перепроверяется, а assets/* с новым хэшем в имени и так не
        // совпадут со старым закэшированным URL. LOAD_NO_CACHE больше не нужен
        // и как раз он не даёт immutable-кэшу вообще когда-либо сработать.
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        runCacheMigrationOnce(webView, context);

        // Android 14 User Agent
        settings.setUserAgentString(UA_ANDROID);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
    }

    /**
     * Разовая миграция для тех, кто успел поставить приложение ДО фикса кэша:
     * один раз стираем то, что могло накопиться при старом LOAD_NO_CACHE-режиме
     * (в частности, устаревшие условные ETag/Last-Modified записи без нового
     * Cache-Control), дальше — не трогаем, иначе это снова свело бы на нет
     * весь смысл immutable-кэша.
     */
    private static void runCacheMigrationOnce(WebView webView, Context context) {
        SharedPreferences prefs = context.getSharedPreferences("cache_migration", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("cleared_once_v1", false)) {
            webView.clearCache(true);
            prefs.edit().putBoolean("cleared_once_v1", true).apply();
        }
    }
}
