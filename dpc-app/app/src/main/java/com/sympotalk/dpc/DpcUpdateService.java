// DPC Foreground Service — 업데이트 상태 머신 실행 진입점
package com.sympotalk.dpc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DpcUpdateService extends Service {

    private static final String TAG            = "DpcUpdateService";
    static final String ACTION_CHECK_UPDATE    = "com.sympotalk.dpc.ACTION_CHECK_UPDATE";
    static final String ACTION_ROLLBACK        = "com.sympotalk.dpc.ACTION_ROLLBACK";
    private static final String CHANNEL_ID     = "dpc_channel";
    private static final int    NOTIF_ID       = 1001;

    private UpdateStateMachine sm;
    private HealthWatchdog watchdog;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("DPC 대기 중"));
        sm = new UpdateStateMachine(this);
        Log.i(TAG, "DpcUpdateService 시작. 현재 상태: " + sm.getState().name());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // 시스템 재시작 — 상태 복원
            handleBootRestore();
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_CHECK_UPDATE.equals(action)) {
            handleCheckUpdate();
        } else if (ACTION_ROLLBACK.equals(action)) {
            handleRollback();
        }
        return START_STICKY;
    }

    private void handleCheckUpdate() {
        UpdateStateMachine.State state = sm.getState();
        Log.i(TAG, "업데이트 체크 요청. 현재 상태: " + state.name());

        if (state != UpdateStateMachine.State.IDLE) {
            Log.w(TAG, "이미 진행 중인 작업 있음 — 무시");
            return;
        }

        sm.transition(UpdateStateMachine.State.CHECKING);
        updateNotification("업데이트 확인 중...");

        new Thread(() -> {
            try {
                // 1. 설치된 런처 버전 조회
                String currentVersion;
                try {
                    PackageInfo info = getPackageManager()
                        .getPackageInfo(BuildConfig.LAUNCHER_PACKAGE, 0);
                    currentVersion = info.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "런처 미설치 — IDLE 복귀");
                    sm.transition(UpdateStateMachine.State.IDLE);
                    return;
                }

                // 2. GitHub Releases API 에서 최신 버전 조회
                String[] release = fetchLatestRelease();
                if (release == null) {
                    Log.w(TAG, "릴리스 조회 실패 — IDLE 복귀");
                    sm.transition(UpdateStateMachine.State.IDLE);
                    return;
                }

                String latestVersion = release[0];
                String apkUrl        = release[1];

                // 3. 버전 비교
                if (!isNewerVersion(latestVersion, currentVersion)) {
                    Log.i(TAG, "최신 버전 사용 중: " + currentVersion);
                    sm.transition(UpdateStateMachine.State.IDLE);
                    return;
                }

                Log.i(TAG, "업데이트 발견: " + currentVersion + " → " + latestVersion);
                sm.setPendingVersion(latestVersion);
                sm.transition(UpdateStateMachine.State.DOWNLOADING);
                updateNotification("다운로드 중: " + latestVersion);

                // 4. APK 다운로드
                new ApkDownloader(DpcUpdateService.this).download(apkUrl, null,
                    new ApkDownloader.Callback() {
                        @Override
                        public void onProgress(int percent) {
                            if (percent % 25 == 0) updateNotification("다운로드 중: " + percent + "%");
                        }
                        @Override
                        public void onSuccess(File apkFile, String sha256) {
                            Log.i(TAG, "다운로드 완료. sha256=" + sha256);
                            sm.setDownloadPath(apkFile.getAbsolutePath());
                            sm.transition(UpdateStateMachine.State.INSTALLING);
                            updateNotification("설치 중...");
                            doSilentInstall(apkFile);
                        }
                        @Override
                        public void onFailure(String reason) {
                            Log.e(TAG, "다운로드 실패: " + reason);
                            sm.transition(UpdateStateMachine.State.IDLE);
                        }
                    }
                );
            } catch (Exception e) {
                Log.e(TAG, "업데이트 체크 예외", e);
                sm.transition(UpdateStateMachine.State.IDLE);
            }
        }).start();
    }

    private void doSilentInstall(final File apkFile) {
        new SilentInstaller(this).install(apkFile, new SilentInstaller.Callback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "설치 성공 — HealthWatchdog 시작");
                sm.resetInstallAttempts();
                startHealthWatchdog();
            }
            @Override
            public void onFailure(String reason) {
                Log.e(TAG, "설치 실패: " + reason);
                sm.incrementInstallAttempts();
                if (sm.getInstallAttempts() < 3) {
                    Log.i(TAG, "재시도 (" + sm.getInstallAttempts() + "/3)");
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> doSilentInstall(apkFile), 10_000);
                } else {
                    Log.e(TAG, "설치 3회 실패 — IDLE 복귀");
                    sm.resetInstallAttempts();
                    sm.transition(UpdateStateMachine.State.IDLE);
                    stopSelf();
                }
            }
        });
    }

    private String[] fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.github.com/repos/"
                + BuildConfig.GITHUB_REPO + "/releases/latest");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "SympotalkDPC/" + BuildConfig.VERSION_NAME);
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API HTTP " + conn.getResponseCode());
                return null;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            String tagName = json.optString("tag_name", "");
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            JSONArray assets = json.optJSONArray("assets");
            String apkUrl = null;
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("SympotalkLauncher.apk".equals(asset.optString("name"))) {
                        apkUrl = asset.optString("browser_download_url");
                        break;
                    }
                }
            }

            if (version.isEmpty() || apkUrl == null || apkUrl.isEmpty()) return null;
            return new String[]{version, apkUrl};

        } catch (Exception e) {
            Log.e(TAG, "GitHub API 실패: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isNewerVersion(String remote, String local) {
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

    private void handleRollback() {
        Log.i(TAG, "롤백 요청");
        sm.setRollbackInProgress(true);
        sm.transition(UpdateStateMachine.State.ROLLING_BACK);
        updateNotification("롤백 진행 중...");

        // 저장된 다운로드 경로가 있으면 재설치 시도
        String path = sm.getDownloadPath();
        if (path != null) {
            File apkFile = new File(path);
            if (apkFile.exists()) {
                Log.i(TAG, "저장된 APK 재설치: " + path);
                sm.transition(UpdateStateMachine.State.INSTALLING);
                doSilentInstall(apkFile);
                return;
            }
        }
        Log.w(TAG, "롤백 가능한 APK 없음 — IDLE 복귀");
        sm.setRollbackInProgress(false);
        sm.transition(UpdateStateMachine.State.IDLE);
        stopSelf();
    }

    private void handleBootRestore() {
        UpdateStateMachine.State state = sm.getState();
        Log.i(TAG, "부팅 복원. 상태: " + state.name());

        if (state == UpdateStateMachine.State.HEALTH_WAIT) {
            // 설치 후 재부팅 중 — HealthWatchdog 재시작
            startHealthWatchdog();
        } else if (state == UpdateStateMachine.State.DOWNLOADING
                || state == UpdateStateMachine.State.VERIFYING
                || state == UpdateStateMachine.State.INSTALLING) {
            // 미완료 상태로 재부팅 — IDLE로 복귀 후 재시도
            Log.w(TAG, "미완료 상태 " + state.name() + " — IDLE 복귀");
            sm.transition(UpdateStateMachine.State.IDLE);
            new Handler(Looper.getMainLooper()).postDelayed(this::handleCheckUpdate, 5_000);
        }
    }

    void startHealthWatchdog() {
        String launcherPackage = BuildConfig.LAUNCHER_PACKAGE;
        watchdog = new HealthWatchdog(this, sm, launcherPackage, new HealthWatchdog.Listener() {
            @Override
            public void onLauncherHealthy() {
                Log.i(TAG, "Launcher 건강 확인 — 업데이트 성공");
                String version = sm.getPendingVersion();
                if (version != null) {
                    sm.markInstallSuccess(version);
                }
                updateNotification("업데이트 완료");
                stopSelf();
            }

            @Override
            public void onLauncherFailed(String reason) {
                Log.e(TAG, "Launcher 장애: " + reason + " — 롤백 시작");
                handleRollback();
            }
        });
        watchdog.start();
    }

    @Override
    public void onDestroy() {
        if (watchdog != null) {
            watchdog.stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "DPC 업데이트", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Launcher 자동 업데이트 상태");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sympotalk DPC")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }
}
