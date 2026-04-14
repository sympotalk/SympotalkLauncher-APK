package com.sympotalk.launcher;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * APK 자동 업데이트 매니저
 * - GitHub Releases API 에서 최신 버전 확인
 * - DownloadManager 로 APK 다운로드
 * - FileProvider 로 설치 인텐트 발행
 * 전제: 모든 APK 가 동일 keystore 로 서명되어야 "업데이트"로 동작 (삭제 불필요)
 */
public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String API_BASE = "https://api.github.com/repos/";
    private static final int TIMEOUT = 8000;

    public interface CheckCallback {
        void onUpdateAvailable(String latestVersion, String apkUrl, String releaseNotes);
        void onUpToDate(String currentVersion);
        void onCheckFailed(String reason);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onDownloadComplete(File apkFile);
        void onDownloadFailed(String reason);
    }

    private final Context context;
    private final String repo;         // "sympotalk/SympotalkLauncher-APK"
    private DownloadManager downloadManager;
    private long currentDownloadId = -1;
    private BroadcastReceiver downloadReceiver;

    public AppUpdateManager(Context context, String repo) {
        this.context = context.getApplicationContext();
        this.repo = repo;
        this.downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** 현재 앱 버전 (BuildConfig.VERSION_NAME 기반) */
    public String getCurrentVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /** GitHub Releases API 에서 최신 버전 확인 */
    public void checkForUpdate(final CheckCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_BASE + repo + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "SympotalkLauncher/" + BuildConfig.VERSION_NAME);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200) {
                    callback.onCheckFailed("GitHub API HTTP " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.optString("tag_name", "");        // e.g. "v1.0.11"
                String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                String releaseNotes = json.optString("body", "");

                // SympotalkLauncher.apk 고정 파일명 자산 찾기
                JSONArray assets = json.optJSONArray("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if ("SympotalkLauncher.apk".equals(name)) {
                            apkUrl = asset.optString("browser_download_url");
                            break;
                        }
                    }
                }

                if (apkUrl == null || apkUrl.isEmpty()) {
                    callback.onCheckFailed("APK 파일을 찾을 수 없습니다");
                    return;
                }

                if (isNewerVersion(latestVersion, getCurrentVersion())) {
                    callback.onUpdateAvailable(latestVersion, apkUrl, releaseNotes);
                } else {
                    callback.onUpToDate(getCurrentVersion());
                }
            } catch (Exception e) {
                Log.w(TAG, "업데이트 확인 실패: " + e.getMessage());
                callback.onCheckFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    /** DownloadManager 로 APK 다운로드 + 진행상황 콜백 */
    public void downloadApk(String apkUrl, final DownloadCallback callback) {
        try {
            // 기존 다운로드 정리
            cancelCurrentDownload();

            // 다운로드 경로: 앱 외부 캐시 (/sdcard/Android/data/<pkg>/cache/)
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) cacheDir = context.getCacheDir();
            File apkFile = new File(cacheDir, "SympotalkLauncher-latest.apk");
            if (apkFile.exists()) apkFile.delete();

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Sympotalk 런처 업데이트")
                .setDescription("최신 APK 다운로드 중")
                .setDestinationUri(Uri.fromFile(apkFile))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType("application/vnd.android.package-archive");

            currentDownloadId = downloadManager.enqueue(req);
            final File finalApkFile = apkFile;

            // 완료 브로드캐스트 리시버
            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id != currentDownloadId) return;
                    cleanupReceiver();

                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                    try (Cursor cursor = downloadManager.query(query)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                callback.onProgress(100);
                                callback.onDownloadComplete(finalApkFile);
                            } else {
                                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                                callback.onDownloadFailed("다운로드 실패 (status=" + status + ", reason=" + reason + ")");
                            }
                        } else {
                            callback.onDownloadFailed("다운로드 상태 조회 실패");
                        }
                    } catch (Exception e) {
                        callback.onDownloadFailed("오류: " + e.getMessage());
                    }
                }
            };

            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(downloadReceiver, filter);
            }

            // 진행률 폴링 (별도 스레드)
            startProgressPolling(currentDownloadId, callback);

        } catch (Exception e) {
            callback.onDownloadFailed("다운로드 시작 실패: " + e.getMessage());
        }
    }

    private void startProgressPolling(final long id, final DownloadCallback callback) {
        new Thread(() -> {
            boolean finished = false;
            int lastPercent = -1;
            while (!finished) {
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_SUCCESSFUL
                                || status == DownloadManager.STATUS_FAILED) {
                            finished = true;
                            continue;
                        }
                        long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        long done  = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        if (total > 0) {
                            int percent = (int) (100L * done / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                callback.onProgress(percent);
                            }
                        }
                    } else {
                        finished = true;
                    }
                } catch (Exception ignored) {
                    finished = true;
                }
            }
        }).start();
    }

    public void cancelCurrentDownload() {
        if (currentDownloadId != -1) {
            try { downloadManager.remove(currentDownloadId); } catch (Exception ignored) {}
            currentDownloadId = -1;
        }
        cleanupReceiver();
    }

    private void cleanupReceiver() {
        if (downloadReceiver != null) {
            try { context.unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
            downloadReceiver = null;
        }
    }

    /** 다운로드된 APK 를 설치 인텐트로 실행 */
    public void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) return;
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        context.startActivity(install);
    }

    /** 버전 비교: "1.2.3" 형식 */
    private boolean isNewerVersion(String remote, String local) {
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
}
