// PackageInstaller 세션 기반 무음 설치 — Device Owner 전용
package com.sympotalk.dpc;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SilentInstaller {

    private static final String TAG         = "SilentInstaller";
    private static final int    BUFFER_SIZE = 65536;
    private static final String ACTION_STATUS_PREFIX = "com.sympotalk.dpc.INSTALL_STATUS.";

    public interface Callback {
        void onSuccess();
        void onFailure(String reason);
    }

    private final Context context;

    public SilentInstaller(Context context) {
        this.context = context.getApplicationContext();
    }

    public void install(final File apkFile, final Callback callback) {
        if (apkFile == null || !apkFile.exists()) {
            callback.onFailure("APK 파일 없음: " + apkFile);
            return;
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        );

        int sessionId;
        try {
            sessionId = installer.createSession(params);
        } catch (IOException e) {
            Log.e(TAG, "세션 생성 실패", e);
            callback.onFailure("세션 생성 실패: " + e.getMessage());
            return;
        }

        final String action = ACTION_STATUS_PREFIX + sessionId;
        BroadcastReceiver resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                context.unregisterReceiver(this);
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                                                PackageInstaller.STATUS_FAILURE);
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.i(TAG, "설치 결과: status=" + status + " msg=" + msg);
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    callback.onSuccess();
                } else {
                    callback.onFailure("설치 실패 (status=" + status + "): " + msg);
                }
            }
        };

        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(resultReceiver, filter);
        }

        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(sessionId);
            writeApk(apkFile, session);

            Intent statusIntent = new Intent(action);
            statusIntent.setPackage(context.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(
                context, sessionId, statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            session.commit(pi.getIntentSender());
            Log.i(TAG, "세션 커밋 완료. sessionId=" + sessionId);
        } catch (Exception e) {
            Log.e(TAG, "설치 세션 실패", e);
            try { context.unregisterReceiver(resultReceiver); } catch (Exception ignored) {}
            installer.abandonSession(sessionId);
            callback.onFailure("설치 예외: " + e.getMessage());
        } finally {
            if (session != null) {
                try { session.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void writeApk(File apkFile, PackageInstaller.Session session) throws IOException {
        try (InputStream in  = new FileInputStream(apkFile);
             OutputStream out = session.openWrite("launcher.apk", 0, apkFile.length())) {
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            session.fsync(out);
        }
    }
}
