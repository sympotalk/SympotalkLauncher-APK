// Launcher cold-start 감시 — startup_pending 마커 + 90초 timeout + rollback 트리거
package com.sympotalk.dpc;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

public class HealthWatchdog {

    private static final String TAG             = "HealthWatchdog";
    private static final long   TIMEOUT_MS      = 90_000L; // 90초
    private static final long   POLL_INTERVAL_MS = 3_000L; // 3초마다 프로세스 확인

    public interface Listener {
        void onLauncherHealthy();
        void onLauncherFailed(String reason);
    }

    private final Context            context;
    private final UpdateStateMachine sm;
    private final String             launcherPackage;
    private final Listener           listener;
    private final Handler            handler;

    private boolean stopped = false;
    private long    startedAt;

    public HealthWatchdog(Context context, UpdateStateMachine sm,
                          String launcherPackage, Listener listener) {
        this.context         = context;
        this.sm              = sm;
        this.launcherPackage = launcherPackage;
        this.listener        = listener;
        this.handler         = new Handler(Looper.getMainLooper());
    }

    /**
     * 감시 시작. startup_pending 마커를 세운 뒤 Launcher 프로세스를 폴링.
     * 90초 내 기동 확인 못 하면 onLauncherFailed 호출.
     */
    public void start() {
        sm.setStartupPending(true);
        sm.transition(UpdateStateMachine.State.HEALTH_WAIT);
        startedAt = System.currentTimeMillis();
        Log.i(TAG, "HealthWatchdog 시작. 타임아웃=" + (TIMEOUT_MS / 1000) + "초");
        scheduleCheck();
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "HealthWatchdog 중지");
    }

    private void scheduleCheck() {
        handler.postDelayed(this::checkLauncher, POLL_INTERVAL_MS);
    }

    private void checkLauncher() {
        if (stopped) return;

        long elapsed = System.currentTimeMillis() - startedAt;

        if (isLauncherRunning()) {
            Log.i(TAG, "Launcher 프로세스 확인됨 (" + elapsed + "ms). 건강 확인 완료.");
            sm.setStartupPending(false);
            stopped = true;
            listener.onLauncherHealthy();
            return;
        }

        if (elapsed >= TIMEOUT_MS) {
            Log.e(TAG, "Launcher가 " + (TIMEOUT_MS / 1000) + "초 내 기동하지 않음 — 롤백 트리거");
            sm.setStartupPending(false);
            stopped = true;
            listener.onLauncherFailed("90초 timeout — 프로세스 없음");
            return;
        }

        Log.d(TAG, "Launcher 미기동 (" + elapsed + "ms 경과). 재폴링...");
        scheduleCheck();
    }

    private boolean isLauncherRunning() {
        ActivityManager am =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;

        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;

        for (ActivityManager.RunningAppProcessInfo proc : procs) {
            if (launcherPackage.equals(proc.processName)
                    && proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                return true;
            }
        }
        return false;
    }
}
