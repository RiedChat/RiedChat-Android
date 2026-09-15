package riedchat.quardinimus.edition;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Держит во WebView только TrustedHosts.HOSTS (там разрешён доступ к JS-мостам
 * AndroidDownloadInterface / AndroidVaultBridge), всё остальное уводит во
 * внешний браузер.
 *
 * БАГ (исправлено): раньше здесь всегда возвращалось false, и ЛЮБАЯ ссылка (в
 * том числе присланная собеседником в чате) открывалась в этом же WebView — с
 * тем же набором addJavascriptInterface-мостов, что и доверенный домен. Чужая
 * страница получала доступ к AndroidVaultBridge и могла бы дёргать биометрию/
 * шифровать-расшифровывать через него. Теперь всё, что не из TrustedHosts,
 * уходит во внешний браузер, где никаких JS-интерфейсов нет.
 */
public class TrustedHostWebViewClient extends WebViewClient {

    private final Activity activity;

    public TrustedHostWebViewClient(Activity activity) {
        this.activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (TrustedHosts.isTrusted(uri.getHost())) {
            return false;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {}
        return true;
    }

    // БАГ: система может убить отдельный renderer-процесс WebView (не всю
    // Activity), пока приложение долго висит в фоне - типично при нехватке
    // памяти. didCrash в этом случае false: это не падение страницы, а плановое
    // убийство системой. Без переопределения этого метода WebView остаётся
    // пустым экраном (весь JS-рантайм, включая App/reconnectIfNeeded, исчезает
    // вместе с процессом), либо, на части версий Android, если didCrash true,
    // системный дефолт - убить весь процесс приложения. Ни то ни другое не
    // лечится реконнект-логикой на JS-стороне, потому что странице уже некому
    // её выполнять.
    //
    // ИСПРАВЛЕНИЕ: сами пересоздаём WebView и грузим страницу заново, если
    // Activity сейчас видима; если она в фоне - просто ничего не делаем и
    // помечаем, что перезагрузка нужна при следующем onResume (иначе
    // reload() на невидимой Activity может не примениться штатно на части
    // прошивок). Возвращаем true - подтверждаем, что сами обработали событие
    // и система не должна убивать процесс приложения.
    @Override
    public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).recreateWebViewAfterRendererGone();
        }
        return true;
    }
}
