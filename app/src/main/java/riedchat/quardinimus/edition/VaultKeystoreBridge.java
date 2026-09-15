package riedchat.quardinimus.edition;

import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.fragment.app.FragmentActivity;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * ===================== VaultKeystoreBridge =====================
 * JS-мост к AES-256-GCM ключу в AndroidKeyStore для riedchat/src/crypto/vault.js
 * (режим 'android-native').
 *
 * Отличие от WebAuthn PRF-режима того же vault.js: там секрет живёт в
 * WebAuthn-аутентификаторе и в JS попадает уже готовый CryptoKey, выведенный
 * через HKDF из PRF-значения. Здесь секрет (AES-ключ) вообще никогда не
 * покидает AndroidKeyStore и в JS не передаётся В ПРИНЦИПЕ — наружу идут
 * только plaintext/ciphertext, а сама операция шифрования/расшифровки
 * выполняется тут, на нативной стороне, за одну и ту же биометрическую
 * аутентификацию, криптографически привязанную к конкретному Cipher
 * (BiometricPrompt.CryptoObject).
 *
 * Ключ и его генерация — в VaultKeyManager, биометрическая аутентификация
 * конкретной Cipher-операции — в VaultBiometricAuthenticator. Этот класс
 * только принимает вызовы из JS, координирует их и формирует ответ обратно
 * в WebView.
 *
 * Мост добавляется в WebView только для доверенного домена приложения —
 * см. MainActivity.shouldOverrideUrlLoading, который не даёт открыть во
 * WebView сторонние ссылки (иначе чужая страница получила бы доступ к этому
 * интерфейсу и могла бы дёргать биометрию/шифровать-расшифровывать через
 * него произвольные данные).
 */
public class VaultKeystoreBridge {

    private static final String TAG = "VaultKeystoreBridge";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final FragmentActivity activity;
    private final WebView webView;
    private final VaultBiometricAuthenticator authenticator;

    public VaultKeystoreBridge(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.authenticator = new VaultBiometricAuthenticator(activity);
    }

    /** Синхронная лёгкая проверка — можно ли вообще использовать этот режим на устройстве. */
    @JavascriptInterface
    public boolean isAvailable() {
        return authenticator.isAvailable();
    }

    /** plaintextUtf8 — открытый JSON-текст объекта, который шифрует vault.js. */
    @JavascriptInterface
    public void encrypt(final String plaintextUtf8, final String requestId) {
        activity.runOnUiThread(() -> {
            try {
                SecretKey key = VaultKeyManager.getOrCreateKey();
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, key);
                authenticator.authenticateThenRun(cipher, authedCipher -> {
                    try {
                        byte[] iv = authedCipher.getIV();
                        byte[] ciphertext = authedCipher.doFinal(
                                plaintextUtf8.getBytes(StandardCharsets.UTF_8));
                        respondEncryptSuccess(requestId, b64(iv), b64(ciphertext));
                    } catch (Exception e) {
                        respondError(requestId, "encrypt doFinal: " + e.getMessage());
                    }
                }, message -> respondError(requestId, message));
            } catch (Exception e) {
                respondError(requestId, "encrypt init: " + e.getMessage());
            }
        });
    }

    /** ivB64/dataB64 — как их отдаёт encrypt() и хранит vault.js в блобе {v,iv,data}. */
    @JavascriptInterface
    public void decrypt(final String ivB64, final String dataB64, final String requestId) {
        activity.runOnUiThread(() -> {
            try {
                SecretKey key = VaultKeyManager.getOrCreateKey();
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64));
                cipher.init(Cipher.DECRYPT_MODE, key, spec);
                authenticator.authenticateThenRun(cipher, authedCipher -> {
                    try {
                        byte[] plaintext = authedCipher.doFinal(unb64(dataB64));
                        respondDecryptSuccess(requestId, new String(plaintext, StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        respondError(requestId, "decrypt doFinal: " + e.getMessage());
                    }
                }, message -> respondError(requestId, message));
            } catch (Exception e) {
                respondError(requestId, "decrypt init: " + e.getMessage());
            }
        });
    }

    /** Вызывается из vault.reset() на JS-стороне при полном сбросе vault. */
    @JavascriptInterface
    public void deleteKey() {
        VaultKeyManager.deleteKey();
    }

    // ---------------------------------------------------------------------

    // Сигнатура JS-коллбэка всегда фиксирована:
    // __androidVaultCallback(requestId, success, ivB64, dataB64, plaintextUtf8, errorMessage)
    private void respondEncryptSuccess(String requestId, String ivB64, String dataB64) {
        String js = "window.__androidVaultCallback && window.__androidVaultCallback("
                + org.json.JSONObject.quote(requestId) + ",true,"
                + org.json.JSONObject.quote(ivB64) + ","
                + org.json.JSONObject.quote(dataB64) + ",null,null);";
        webView.evaluateJavascript(js, null);
    }

    private void respondDecryptSuccess(String requestId, String plaintextUtf8) {
        String js = "window.__androidVaultCallback && window.__androidVaultCallback("
                + org.json.JSONObject.quote(requestId) + ",true,null,null,"
                + org.json.JSONObject.quote(plaintextUtf8) + ",null);";
        webView.evaluateJavascript(js, null);
    }

    private void respondError(String requestId, String message) {
        Log.w(TAG, "vault bridge error: " + message);
        String js = "window.__androidVaultCallback && window.__androidVaultCallback("
                + org.json.JSONObject.quote(requestId) + ",false,null,null,null,"
                + org.json.JSONObject.quote(message) + ");";
        webView.evaluateJavascript(js, null);
    }

    private static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
