package riedchat.quardinimus.edition;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * Скачивание blob: URL, которые может отдать только JS самой страницы —
 * native-код не может резолвить "blob:" сам по себе (это не сетевая схема,
 * данные существуют только в контексте создавшей их страницы). Просит
 * страницу прочитать blob через fetch/XHR + FileReader и вернуть base64
 * через @JavascriptInterface, затем пишет результат в Downloads.
 */
final class BlobDownloader {

    private static final String TAG = "BlobDownloader";

    private final Activity activity;
    private final WebView webView;
    private final DownloadsWriter downloadsWriter;

    BlobDownloader(Activity activity, WebView webView, DownloadsWriter downloadsWriter) {
        this.activity = activity;
        this.webView = webView;
        this.downloadsWriter = downloadsWriter;
        webView.addJavascriptInterface(new BlobDownloadBridge(), "AndroidDownloadInterface");
    }

    void downloadBlobUrl(String blobUrl, String fileName, String mimeType) {
        Toast.makeText(activity, "Загрузка начата: " + fileName, Toast.LENGTH_SHORT).show();

        String js = "(function(){"
                + "try{"
                + "var xhr=new XMLHttpRequest();"
                + "xhr.open('GET','" + escapeJs(blobUrl) + "',true);"
                + "xhr.responseType='blob';"
                + "xhr.onload=function(){"
                + "  if(xhr.status!==200&&xhr.status!==0){AndroidDownloadInterface.onError('http '+xhr.status);return;}"
                + "  var reader=new FileReader();"
                + "  reader.onloadend=function(){"
                + "    var res=reader.result||'';"
                + "    var i=res.indexOf(',');"
                + "    var b64=i>=0?res.substring(i+1):'';"
                + "    AndroidDownloadInterface.saveBase64(b64,'" + escapeJs(fileName) + "','" + escapeJs(mimeType) + "');"
                + "  };"
                + "  reader.onerror=function(){AndroidDownloadInterface.onError('reader error');};"
                + "  reader.readAsDataURL(xhr.response);"
                + "};"
                + "xhr.onerror=function(){AndroidDownloadInterface.onError('xhr error');};"
                + "xhr.send();"
                + "}catch(e){AndroidDownloadInterface.onError(String(e));}"
                + "})();";

        webView.evaluateJavascript(js, null);
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "");
    }

    /** JS-facing bridge for downloadBlobUrl(). */
    private class BlobDownloadBridge {
        @JavascriptInterface
        public void saveBase64(final String base64Data, final String fileName, final String mimeType) {
            new Thread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                    downloadsWriter.writeBytesToDownloads(bytes, fileName, mimeType);
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Файл сохранён в Загрузки: " + fileName, Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    Log.e(TAG, "saveBase64 failed for " + fileName, e);
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Не удалось скачать файл: " + e, Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        @JavascriptInterface
        public void onError(final String message) {
            Log.e(TAG, "blob download JS error: " + message);
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    "Не удалось скачать файл: " + message, Toast.LENGTH_LONG).show());
        }
    }
}
