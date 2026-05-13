// 업데이트 상태 머신 — SharedPreferences로 상태 지속, 재부팅 후 복원 가능
package com.sympotalk.dpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class UpdateStateMachine {

    private static final String TAG = "UpdateStateMachine";
    private static final String PREFS_NAME = "dpc_state";

    // SharedPreferences 키 목록
    static final String KEY_STATE               = "state";
    static final String KEY_STARTUP_PENDING     = "startup_pending";
    static final String KEY_ROLLBACK_IN_PROGRESS = "rollback_in_progress";
    static final String KEY_PENDING_VERSION     = "pending_version";
    static final String KEY_LAST_KNOWN_GOOD     = "last_known_good_version";
    static final String KEY_INSTALL_ATTEMPTS    = "install_attempt_count";
    static final String KEY_DOWNLOAD_PATH       = "download_path";

    public enum State {
        IDLE,
        CHECKING,
        DOWNLOADING,
        VERIFYING,
        INSTALLING,
        HEALTH_WAIT,
        ROLLING_BACK
    }

    private final SharedPreferences prefs;

    public UpdateStateMachine(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public State getState() {
        String name = prefs.getString(KEY_STATE, State.IDLE.name());
        try {
            return State.valueOf(name);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "알 수 없는 상태값 '" + name + "' — IDLE로 복원");
            return State.IDLE;
        }
    }

    public void transition(State next) {
        State current = getState();
        Log.i(TAG, current.name() + " → " + next.name());
        prefs.edit().putString(KEY_STATE, next.name()).apply();
    }

    // --- 개별 플래그 접근자 ---

    public boolean isStartupPending() {
        return prefs.getBoolean(KEY_STARTUP_PENDING, false);
    }

    public void setStartupPending(boolean value) {
        prefs.edit().putBoolean(KEY_STARTUP_PENDING, value).apply();
    }

    public boolean isRollbackInProgress() {
        return prefs.getBoolean(KEY_ROLLBACK_IN_PROGRESS, false);
    }

    public void setRollbackInProgress(boolean value) {
        prefs.edit().putBoolean(KEY_ROLLBACK_IN_PROGRESS, value).apply();
    }

    public String getPendingVersion() {
        return prefs.getString(KEY_PENDING_VERSION, null);
    }

    public void setPendingVersion(String version) {
        prefs.edit().putString(KEY_PENDING_VERSION, version).apply();
    }

    public String getLastKnownGoodVersion() {
        return prefs.getString(KEY_LAST_KNOWN_GOOD, null);
    }

    public void setLastKnownGoodVersion(String version) {
        prefs.edit().putString(KEY_LAST_KNOWN_GOOD, version).apply();
    }

    public int getInstallAttempts() {
        return prefs.getInt(KEY_INSTALL_ATTEMPTS, 0);
    }

    public void incrementInstallAttempts() {
        prefs.edit().putInt(KEY_INSTALL_ATTEMPTS, getInstallAttempts() + 1).apply();
    }

    public void resetInstallAttempts() {
        prefs.edit().putInt(KEY_INSTALL_ATTEMPTS, 0).apply();
    }

    public String getDownloadPath() {
        return prefs.getString(KEY_DOWNLOAD_PATH, null);
    }

    public void setDownloadPath(String path) {
        prefs.edit().putString(KEY_DOWNLOAD_PATH, path).apply();
    }

    /** 설치 성공 후 정리 — IDLE 복귀 */
    public void markInstallSuccess(String version) {
        prefs.edit()
            .putString(KEY_STATE, State.IDLE.name())
            .putString(KEY_LAST_KNOWN_GOOD, version)
            .putBoolean(KEY_STARTUP_PENDING, false)
            .putBoolean(KEY_ROLLBACK_IN_PROGRESS, false)
            .putInt(KEY_INSTALL_ATTEMPTS, 0)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_DOWNLOAD_PATH)
            .apply();
        Log.i(TAG, "설치 성공 — last_known_good=" + version);
    }
}
