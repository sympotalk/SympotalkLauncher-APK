package com.sympotalk.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Cloudflare Pages에서 최신 웹 앱을 다운로드하는 업데이트 매니저
 * - /version.txt 를 확인해서 로컬 버전과 비교
 * - 신버전이면 index.html, sw.js, manifest.json 다운로드
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String PREFS_NAME = "SympotalkPrefs";
    private static final String PREF_VERSION = "app_version";
    private static final int TIMEOUT_CONNECT = 6000;
    private static final int TIMEOUT_READ = 20000;
    private static final String[] UPDATE_FILES = {
        "index.html", "sw.js", "manifest.json", "version.txt"
    };

    public interface Callback {
        void onUpdateAvailable(String newVersion);
        void onUpdateComplete(String newVersion);
        void onUpdateFailed(Exception e);
        void onUpToDate(String version);
    }

    private final Context context;
    private final String cloudflareBaseUrl;

    public UpdateManager(Context context, String cloudflareBaseUrl) {
        this.context = context.getApplicationContext();
        this.cloudflareBaseUrl = cloudflareBaseUrl.replaceAll("/$", "");
    }

    /** 백그라운드 스레드에서 업데이트 확인 */
    public void checkAndUpdate(Callback callback) {
        new Thread(() -> {
            try {
                String remoteVersion = fetchText(cloudflareBaseUrl + "/version.txt");
                if (remoteVersion == null) {
                    callback.onUpdateFailed(new Exception("버전 파일을 읽을 수 없습니다"));
                    return;
                }
                remoteVersion = remoteVersion.trim();

                String localVersion = getLocalVersion();
                Log.d(TAG, "local=" + localVersion + " remote=" + remoteVersion);

                if (isNewer(remoteVersion, localVersion)) {
                    callback.onUpdateAvailable(remoteVersion);
                    downloadAllFiles(remoteVersion);
                    callback.onUpdateComplete(remoteVersion);
                } else {
                    callback.onUpToDate(localVersion);
                }
            } catch (Exception e) {
                Log.w(TAG, "업데이트 확인 실패: " + e.getMessage());
                callback.onUpdateFailed(e);
            }
        }).start();
    }

    /** 업데이트된 index.html 경로 (없으면 null → assets 사용) */
    public File getUpdatedIndexFile() {
        File f = new File(getWwwDir(), "index.html");
        return f.exists() ? f : null;
    }

    private void downloadAllFiles(String version) throws Exception {
        File wwwDir = getWwwDir();
        if (!wwwDir.exists()) wwwDir.mkdirs();

        for (String filename : UPDATE_FILES) {
            String fileUrl = cloudflareBaseUrl + "/" + filename;
            File outFile = new File(wwwDir, filename);
            downloadFile(fileUrl, outFile);
            Log.d(TAG, "다운로드 완료: " + filename);
        }
        saveLocalVersion(version);
    }

    private void downloadFile(String fileUrl, File outFile) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_CONNECT);
        conn.setReadTimeout(TIMEOUT_READ);
        conn.setRequestProperty("User-Agent", "SympotalkLauncher/" + BuildConfig.VERSION_NAME + " Android");
        conn.connect();

        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode() + " for " + fileUrl);
        }

        File tmpFile = new File(outFile.getParent(), outFile.getName() + ".tmp");
        try (InputStream is = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(tmpFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }
        // 원자적 교체
        if (outFile.exists()) outFile.delete();
        tmpFile.renameTo(outFile);
    }

    private String fetchText(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_CONNECT);
            conn.setReadTimeout(TIMEOUT_READ);
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.connect();
            if (conn.getResponseCode() != 200) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 버전 비교: "1.2.3" 형식 */
    private boolean isNewer(String remote, String local) {
        try {
            String[] r = remote.split("\\.");
            String[] l = local.split("\\.");
            int len = Math.max(r.length, l.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length ? Integer.parseInt(r[i].replaceAll("[^0-9]", "")) : 0;
                int lv = i < l.length ? Integer.parseInt(l[i].replaceAll("[^0-9]", "")) : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String getLocalVersion() {
        // 다운로드된 version.txt 우선
        File vf = new File(getWwwDir(), "version.txt");
        if (vf.exists()) {
            try {
                BufferedReader br = new BufferedReader(new java.io.FileReader(vf));
                String v = br.readLine();
                br.close();
                if (v != null) return v.trim();
            } catch (Exception ignored) {}
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_VERSION, "0.0.0");
    }

    private void saveLocalVersion(String version) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_VERSION, version).apply();
    }

    private File getWwwDir() {
        return new File(context.getFilesDir(), "www");
    }
}
