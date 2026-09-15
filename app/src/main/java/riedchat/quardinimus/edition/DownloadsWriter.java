package riedchat.quardinimus.edition;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Пишет сырые байты в публичную папку Downloads: на Android 10+ через
 * MediaStore (без разрешений), ниже — напрямую в публичную директорию
 * Downloads по уже выданному разрешению WRITE_EXTERNAL_STORAGE.
 */
final class DownloadsWriter {

    private static final String TAG = "DownloadsWriter";

    private final Activity activity;

    DownloadsWriter(Activity activity) {
        this.activity = activity;
    }

    void writeBytesToDownloads(byte[] bytes, String fileName, String mimeType) throws IOException {
        OutputStream out = null;
        ContentResolver resolver = null;
        Uri itemUri = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver = activity.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                // На части прошивок (Samsung One UI / MIUI на Android 13) запись без
                // IS_PENDING=1 приводит к тому, что MediaProvider отдаёт битый/0-байтный
                // файл или откатывает вставку до завершения записи.
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (itemUri == null) throw new IOException("MediaStore insert failed");
                out = resolver.openOutputStream(itemUri);
                if (out == null) throw new IOException("Could not open output stream");
                out.write(bytes);
                out.flush();
                out.close();
                out = null;
                ContentValues doneValues = new ContentValues();
                doneValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(itemUri, doneValues, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                out = new FileOutputStream(new File(dir, fileName));
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "writeBytesToDownloads failed for " + fileName, e);
            if (itemUri != null && resolver != null) {
                try { resolver.delete(itemUri, null, null); } catch (Exception ignored) {}
            }
            throw e;
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
        }
    }
}
