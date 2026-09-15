package riedchat.quardinimus.edition;

import android.webkit.JavascriptInterface;

/**
 * ===================== CallStateBridge =====================
 * JS-мост, которым src/features/call/call-manager.js сообщает нативной
 * стороне, идёт ли сейчас звонок (setCall() на каждый переход S.call).
 *
 * Зачем: MainActivity.onPause() сбрасывает AudioManager обратно в
 * MODE_NORMAL при любом уходе Activity в фон — в том числе на те доли
 * секунды, когда поверх неё показывается системный диалог запроса
 * разрешений CAMERA/RECORD_AUDIO. Без этого моста onPause() не может
 * отличить "приложение свернули во время звонка" от "звонка вообще нет
 * сейчас", и откатывает аудио-режим ровно тогда, когда WebView только
 * получил доступ к микрофону — из-за чего getUserMedia в звонке падает
 * с NotReadableError, call-manager.js уходит в receive-only и клиент
 * перестаёт отдавать в PeerConnection свои видео/аудио треки, хотя сам
 * звонок (ICE/DTLS) остаётся установлен и медиа собеседника продолжает
 * приходить — см. MainActivity.onPause()/setCallActive().
 */
public class CallStateBridge {

    private final MainActivity activity;

    public CallStateBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void setCallActive(boolean active) {
        activity.runOnUiThread(() -> activity.setCallActive(active));
    }
}
