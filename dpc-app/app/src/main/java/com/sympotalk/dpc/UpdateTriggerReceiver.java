// 런처로부터 업데이트 트리거 수신 — DpcUpdateService 시작 위임
package com.sympotalk.dpc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UpdateTriggerReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdateTriggerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DpcUpdateService.ACTION_CHECK_UPDATE.equals(intent.getAction())) {
            Log.i(TAG, "런처로부터 업데이트 요청 수신 — 서비스 시작");
            DpcAdminReceiver.startUpdateService(context);
        }
    }
}
