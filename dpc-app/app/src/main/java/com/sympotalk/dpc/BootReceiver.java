// BOOT_COMPLETED 수신 → SharedPreferences 상태 복원 후 업데이트 서비스 재개
package com.sympotalk.dpc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Log.i(TAG, "부팅 완료 — DPC 상태 복원 시작");

        UpdateStateMachine sm = new UpdateStateMachine(context);
        UpdateStateMachine.State state = sm.getState();
        Log.i(TAG, "복원된 상태: " + state.name());

        // HEALTH_WAIT 중 재부팅 → 서비스가 handleBootRestore()에서 HealthWatchdog 재시작
        if (state == UpdateStateMachine.State.HEALTH_WAIT) {
            Log.i(TAG, "HEALTH_WAIT 복원 — DpcUpdateService 재시작");
        }

        DpcAdminReceiver.startUpdateService(context);
    }
}
