package com.sympotalk.launcher;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    // v1.1.1: rate-limit 캐시 폴백 — 마지막 성공 응답을 60분간 신뢰값으로 사용
    private static final String PREF_NAME = "app_update_cache";
    private static final String PREF_LAST_RESPONSE = "last_release_json";
    private static final String PREF_LAST_FETCH_MS = "last_fetch_ms";
    private static final long CACHE_TTL_MS = 60L * 60L * 1000L; // 60분

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

    /**
     * GitHub Releases API 에서 최신 버전 확인.
     * v1.1.1: rate-limit (HTTP 403) 우아한 처리 + 마지막 성공 응답 60분 캐시 폴백.
     *
     * 403 시나리오:
     *  - GitHub Unauthenticated API: 60 req/h per IP. 행사장 NAT 다중 태블릿이면
     *    빠르게 소진. 1시간 슬라이딩 윈도우라 reset 시각만 알리면 사용자가 기다리면 됨.
     *  - 응답 헤더 X-RateLimit-Reset (unix timestamp) 파싱해서 사용자에게 표시.
     *  - 캐시된 응답이 60분 이내면 그것으로 폴백 (정보 표시 OK, 다운로드 URL 도 유효).
     */
    public void checkForUpdate(final CheckCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_BASE + repo + "/releases/latest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "SympotalkLauncher/" + BuildConfig.VERSION_NAME);
                conn.connect();

                int code = conn.getResponseCode();
                if (code == 403 || code == 429) {
                    // Rate limit → reset 시각 안내 + 캐시 폴백 시도
                    String resetHeader = conn.getHeaderField("X-RateLimit-Reset");
                    String remainHeader = conn.getHeaderField("X-RateLimit-Remaining");
                    Log.w(TAG, "GitHub API rate limit (HTTP " + code +
                        "), reset=" + resetHeader + ", remaining=" + remainHeader);

                    String resetMsg = formatRateLimitReset(resetHeader);
                    boolean usedCache = tryCachedResponse(callback, " (캐시) — GitHub API 한도 초과, " + resetMsg + " 이후 자동 재시도 가능");
                    if (!usedCache) {
                        callback.onCheckFailed(
                            "GitHub API 요청 한도 초과 (HTTP " + code + ") — " + resetMsg + " 이후 자동 재시도 가능"
                        );
                    }
                    return;
                }
                if (code != 200) {
                    boolean usedCache = tryCachedResponse(callback, " (캐시) — GitHub API HTTP " + code);
                    if (!usedCache) {
                        callback.onCheckFailed("GitHub API HTTP " + code);
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                conn = null;

                String body = sb.toString();
                // 성공 응답 캐시 (다음 rate-limit 시 폴백용)
                saveCachedResponse(body);

                handleParsedResponse(body, "", callback);

            } catch (Exception e) {
                Log.w(TAG, "업데이트 확인 실패: " + e.getMessage());
                // 네트워크 에러 시에도 캐시 폴백 시도 (앱이 오프라인 출발 후 캐시 있을 때 유용)
                boolean usedCache = tryCachedResponse(callback, " (캐시) — 네트워크 오류: " + e.getMessage());
                if (!usedCache) {
                    callback.onCheckFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    /** GitHub API 캐시 폴백: 60분 이내 캐시가 있으면 그것으로 응답을 만든다. */
    private boolean tryCachedResponse(CheckCallback callback, String suffix) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String cached = prefs.getString(PREF_LAST_RESPONSE, null);
            long fetchedAt = prefs.getLong(PREF_LAST_FETCH_MS, 0L);
            if (cached == null || cached.isEmpty()) return false;
            long age = System.currentTimeMillis() - fetchedAt;
            if (age > CACHE_TTL_MS) return false;
            handleParsedResponse(cached, suffix, callback);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "캐시 폴백 실패: " + e.getMessage());
            return false;
        }
    }

    private void saveCachedResponse(String body) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putString(PREF_LAST_RESPONSE, body)
                .putLong(PREF_LAST_FETCH_MS, System.currentTimeMillis())
                .apply();
        } catch (Exception ignored) {}
    }

    private void handleParsedResponse(String body, String suffix, CheckCallback callback) {
        try {
            JSONObject json = new JSONObject(body);
            String tagName = json.optString("tag_name", "");
            String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String releaseNotes = json.optString("body", "");
            if (suffix != null && !suffix.isEmpty()) {
                releaseNotes = (releaseNotes == null ? "" : releaseNotes) + "\n\n" + suffix.trim();
            }

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
            callback.onCheckFailed("응답 파싱 실패: " + e.getMessage());
        }
    }

    /** X-RateLimit-Reset (unix timestamp) → "HH:mm" 형식 문자열. */
    private String formatRateLimitReset(String resetHeader) {
        if (resetHeader == null || resetHeader.isEmpty()) return "잠시 후";
        try {
            long resetSec = Long.parseLong(resetHeader);
            long resetMs = resetSec * 1000L;
            long now = System.currentTimeMillis();
            long deltaMin = Math.max(0, (resetMs - now) / 60000L);
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.KOREA);
            return fmt.format(new Date(resetMs)) + " (약 " + deltaMin + "분 후)";
        } catch (Exception e) {
            return "잠시 후";
        }
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
                    } finally {
                        currentDownloadId = -1;   // 완료/실패 시 ID 리셋
                    }
                }
            };

            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // DownloadManager 는 시스템 → 앱 브로드캐스트라 NOT_EXPORTED 가 적절
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
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
