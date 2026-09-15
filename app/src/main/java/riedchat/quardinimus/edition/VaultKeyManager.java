package riedchat.quardinimus.edition;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Генерация и хранение AES-256-GCM ключа в AndroidKeyStore для VaultKeystoreBridge.
 * Ключ никогда не покидает Keystore, per-operation аутентификация настраивается
 * в generateKey() (см. комментарии внутри — разница API 30+/ниже).
 */
final class VaultKeyManager {

    private static final String TAG = "VaultKeyManager";
    static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    // v2: сменили спецификацию генерации ключа (добавили поддержку
    // DEVICE_CREDENTIAL/PIN через setUserAuthenticationParameters), старый
    // ключ под алиасом v1 создан по спецификации, которая PIN не
    // поддерживает. Смена алиаса форсирует генерацию нового ключа у всех,
    // кто уже пользовался android-native режимом до этого фикса — ценой
    // одного разового "войдите заново".
    static final String KEY_ALIAS = "riedchat_vault_key_v2";
    private static final String LEGACY_KEY_ALIAS_V1 = "riedchat_vault_key_v1";

    private VaultKeyManager() {
    }

    static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
        ks.load(null);
        SecretKey existing = (SecretKey) ks.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;

        // Best-effort: убираем осиротевший ключ v1 — он больше не используется.
        try {
            if (ks.containsAlias(LEGACY_KEY_ALIAS_V1)) ks.deleteEntry(LEGACY_KEY_ALIAS_V1);
        } catch (Exception e) {
            Log.w(TAG, "cleanup legacy v1 key: " + e.getMessage());
        }

        try {
            return generateKey(true);
        } catch (StrongBoxUnavailableException e) {
            Log.w(TAG, "StrongBox недоступен на этом устройстве, создаём ключ в обычном TEE");
            return generateKey(false);
        }
    }

    static void deleteKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
            if (ks.containsAlias(LEGACY_KEY_ALIAS_V1)) ks.deleteEntry(LEGACY_KEY_ALIAS_V1);
        } catch (Exception e) {
            Log.w(TAG, "deleteKey: " + e.getMessage());
        }
    }

    private static SecretKey generateKey(boolean strongBox) throws Exception {
        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true);

        // БАГ (исправлено): setUserAuthenticationValidityDurationSeconds(-1) —
        // старый способ потребовать аутентификацию НА КАЖДУЮ операцию через
        // CryptoObject. В этой связке ключ может быть авторизован только
        // BIOMETRIC_STRONG; DEVICE_CREDENTIAL (PIN/пароль) такую
        // криптооперацию авторизовать не может, даже если BiometricPrompt
        // разрешает выбрать PIN и показывает "успех" — Cipher остаётся
        // неавторизованным, и doFinal() падает. Отсюда: "ввёл PIN, а не
        // пускает — хотя отпечаток работает".
        //
        // setUserAuthenticationParameters(timeout, type) (API 30+) умеет
        // привязывать per-operation-аутентификацию сразу к обоим типам.
        // timeout=0 — эквивалент старого "-1" (обязательная авторизация
        // каждой операции, а не разовая на N секунд).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            // До API 30 setUserAuthenticationParameters недоступен: PIN-путь
            // через CryptoObject платформой не поддерживается — только
            // биометрия (см. VaultBiometricAuthenticator.allowedAuthenticators()).
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1);
        }

        if (strongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(true);
        }

        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        kg.init(specBuilder.build());
        return kg.generateKey();
    }
}
