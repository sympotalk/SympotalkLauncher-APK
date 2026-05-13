// DPC Foreground Service — 업데이트 상태 머신 실행 진입점
package com.sympotalk.dpc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

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

        // TODO: manifest 서버에서 최신 버전 조회 후 DOWNLOADING 전이
        // 현재는 플레이스홀더 — 실제 구현은 다음 단계
        Log.i(TAG, "TODO: manifest 조회 → DOWNLOADING 전이");
    }

    private void handleRollback() {
        Log.i(TAG, "롤백 요청");
        sm.setRollbackInProgress(true);
        sm.transition(UpdateStateMachine.State.ROLLING_BACK);
        updateNotification("롤백 진행 중...");

        // TODO: last_known_good APK 재설치 흐름 구현
        Log.i(TAG, "TODO: last_known_good 버전으로 PackageInstaller 재설치");
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
