package riedchat.quardinimus.edition;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Скачивание файлов из WebView: и обычных http(s)-ссылок, и blob: URL
 * (делегируется BlobDownloader — см. его javadoc, почему для blob: нужен
 * отдельный путь через JS страницы). Запись байтов в Downloads —
 * в DownloadsWriter.
 */
public class DownloadManagerHelper {

    private static final String TAG = "DownloadManagerHelper";
    public static final int STORAGE_PERMISSION_REQUEST = 2;

    private final Activity activity;
    private final DownloadsWriter downloadsWriter;
    private final BlobDownloader blobDownloader;

    // Pending download info, used if we had to ask for permission first
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    public DownloadManagerHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.downloadsWriter = new DownloadsWriter(activity);
        this.blobDownloader = new BlobDownloader(activity, webView, downloadsWriter);

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                startFileDownload(url, userAgent, contentDisposition, mimeType));
    }

    public void startFileDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (url == null) return;

        final String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        final String resolvedMimeType = (mimeType == null || mimeType.isEmpty())
                ? "application/octet-stream" : mimeType;

        // This chat app decrypts every attachment (OMEMO/AES-GCM) client-side
        // in JS and exposes it only as a blob: URL (see js/net/media.js /
        // js/ui/chat-view.js). blob: URLs are only resolvable *inside the page
        // that created them* — see BlobDownloader for the full explanation.
        if (url.startsWith("blob:")) {
            blobDownloader.downloadBlobUrl(url, fileName, resolvedMimeType);
            return;
        }

        // Plain http(s) links (not used for chat attachments in this app right
        // now, but kept as a fallback) still go through the permission dance +
        // manual download below.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingDownloadUrl = url;
            pendingDownloadUserAgent = userAgent;
            pendingDownloadContentDisposition = contentDisposition;
            pendingDownloadMimeType = mimeType;
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        enqueueDownload(url, userAgent, contentDisposition, mimeType);
    }

    /** Вызывается из Activity#onRequestPermissionsResult. */
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != STORAGE_PERMISSION_REQUEST) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingDownloadUrl != null) {
            enqueueDownload(pendingDownloadUrl, pendingDownloadUserAgent,
                    pendingDownloadContentDisposition, pendingDownloadMimeType);
        } else {
            Toast.makeText(activity, "Нет разрешения на сохранение файла", Toast.LENGTH_SHORT).show();
        }
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimeType = null;
    }

    /**
     * Downloads the file ourselves instead of handing it off to the system
     * DownloadManager. DownloadManager.enqueue() throws on many Android 12+
     * devices (a long-standing AOSP/OEM bug related to PendingIntent
     * mutability flags on the completed-download notification), which was
     * silently swallowed by the old try/catch and made downloads look like
     * they simply didn't work. This version streams the file itself and,
     * on Android 10+, writes it through MediaStore so no storage permission
     * is required and it still shows up in the public Downloads folder.
     */
    private void enqueueDownload(final String url, final String userAgent,
            final String contentDisposition, final String mimeType) {

        final String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        final String cookie = CookieManager.getInstance().getCookie(url);
        final String resolvedMimeType = (mimeType == null || mimeType.isEmpty())
                ? "application/octet-stream" : mimeType;

        Toast.makeText(activity, "Загрузка начата: " + fileName, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            InputStream in = null;
            try {
                URL downloadUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.setRequestProperty("User-Agent",
                        userAgent != null ? userAgent : WebViewConfigurator.UA_ANDROID);
                if (cookie != null) {
                    connection.setRequestProperty("Cookie", cookie);
                }
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code);
                }

                in = connection.getInputStream();
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }

                downloadsWriter.writeBytesToDownloads(buffer.toByteArray(), fileName, resolvedMimeType);

                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Файл сохранён в Загрузки: " + fileName, Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                android.util.Log.e(TAG, "enqueueDownload failed for " + fileName, e);
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Не удалось скачать файл: " + e, Toast.LENGTH_LONG).show());
            } finally {
                try { if (in != null) in.close(); } catch (IOException ignored) {}
            }
        }).start();
    }
}
