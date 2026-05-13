// Device Owner 진입점 — 활성화/비활성화 콜백 + 부팅 후 서비스 시작
package com.sympotalk.dpc;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DpcAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DpcAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device Owner 활성화됨 — 업데이트 서비스 시작");
        startUpdateService(context);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.w(TAG, "Device Owner 비활성화됨 — 자동 업데이트 중단");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // BOOT_COMPLETED는 BootReceiver가 처리. 여기서는 admin 콜백만 다룸.
    }

    static void startUpdateService(Context context) {
        Intent serviceIntent = new Intent(context, DpcUpdateService.class);
        serviceIntent.setAction(DpcUpdateService.ACTION_CHECK_UPDATE);
        context.startForegroundService(serviceIntent);
    }
}
