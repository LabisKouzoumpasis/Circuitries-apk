package com.labiskouzoumpasis.circuitries;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Self-update for an app distributed through GitHub releases: reports the installed version,
 * downloads a new APK and hands it to the Android package installer ("Update this app?").
 */
@CapacitorPlugin(name = "AppUpdater")
public class AppUpdaterPlugin extends Plugin {

    // Only APKs published by this project's own releases may be installed
    private static final String ALLOWED_PREFIX = "https://github.com/LabisKouzoumpasis/Circuitries-apk/releases/download/";
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PluginMethod
    public void getInfo(PluginCall call) {
        try {
            PackageInfo info = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            JSObject result = new JSObject();
            result.put("versionName", info.versionName);
            result.put("versionCode", code);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Could not read app version", e);
        }
    }

    /** Android 8+ needs the per-app "Install unknown apps" permission before it will install an update. */
    @PluginMethod
    public void canInstall(PluginCall call) {
        boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            || getContext().getPackageManager().canRequestPackageInstalls();
        JSObject result = new JSObject();
        result.put("allowed", allowed);
        call.resolve(result);
    }

    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !url.startsWith(ALLOWED_PREFIX)) {
            call.reject("Update URL not allowed");
            return;
        }
        executor.execute(() -> {
            File dir = new File(getContext().getCacheDir(), "updates");
            File apk = new File(dir, "update.apk");
            try {
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create " + dir);
                download(url, apk);
                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apk);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, APK_MIME);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
                call.resolve();
            } catch (Exception e) {
                apk.delete();
                call.reject("Download failed: " + e.getMessage(), e);
            }
        });
    }

    private void download(String url, File target) throws Exception {
        // GitHub redirects release downloads to its asset host (https → https, followed automatically)
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new Exception("HTTP " + status);
            long total = conn.getContentLengthLong();
            long done = 0;
            int lastPercent = -1;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    int percent = total > 0 ? (int) (done * 100 / total) : -1;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        JSObject progress = new JSObject();
                        progress.put("percent", percent);
                        progress.put("bytes", done);
                        notifyListeners("downloadProgress", progress);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }
}
