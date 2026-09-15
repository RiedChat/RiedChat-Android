package riedchat.quardinimus.edition;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Домены, единственные имеющие доступ к JS-мостам (AndroidDownloadInterface,
 * AndroidVaultBridge) — см. TrustedHostWebViewClient.
 */
public final class TrustedHosts {

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
        "riedchat.github.io"
    ));

    private TrustedHosts() {}

    public static boolean isTrusted(String host) {
        return host != null && HOSTS.contains(host);
    }
}
