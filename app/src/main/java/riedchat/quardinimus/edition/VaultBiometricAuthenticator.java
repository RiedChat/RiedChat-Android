package riedchat.quardinimus.edition;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import javax.crypto.Cipher;

/**
 * Биометрическая аутентификация конкретной Cipher-операции (per-operation,
 * через BiometricPrompt.CryptoObject) для VaultKeystoreBridge.
 */
final class VaultBiometricAuthenticator {

    interface CipherCallback {
        void run(Cipher authedCipher);
    }

    interface ErrorCallback {
        void run(String message);
    }

    private final FragmentActivity activity;

    VaultBiometricAuthenticator(FragmentActivity activity) {
        this.activity = activity;
    }

    // BIOMETRIC_STRONG|DEVICE_CREDENTIAL как единая комбинация поддерживается
    // BiometricManager/BiometricPrompt/KeyGenParameterSpec только с API 30+.
    // На API < 30 сознательно не предлагаем PIN как вариант вообще — только
    // биометрия — чтобы не приводить пользователя к "ввёл PIN, а не пускает".
    static int allowedAuthenticators() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        }
        return BiometricManager.Authenticators.BIOMETRIC_STRONG;
    }

    boolean isAvailable() {
        BiometricManager bm = BiometricManager.from(activity);
        return bm.canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    void authenticateThenRun(Cipher cipher, CipherCallback onSuccess, ErrorCallback onError) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Разблокировка RiedChat")
                .setSubtitle(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                        ? "Подтвердите отпечатком, лицом или PIN устройства"
                        : "Подтвердите отпечатком или лицом")
                .setAllowedAuthenticators(allowedAuthenticators())
                .build();

        BiometricPrompt prompt = new BiometricPrompt(activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        Cipher authedCipher = result.getCryptoObject() != null
                                ? result.getCryptoObject().getCipher() : null;
                        if (authedCipher == null) {
                            onError.run("biometric: cipher отсутствует в результате аутентификации");
                            return;
                        }
                        onSuccess.run(authedCipher);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        onError.run("biometric error " + errorCode + ": " + errString);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Один неудачный скан — не финал; система сама даёт повторить
                        // попытку, итоговая неудача придёт в onAuthenticationError.
                    }
                });

        prompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }
}
