// APK 다운로드 + sha256 검증 + ATOMIC_MOVE 스테이징
package com.sympotalk.dpc;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ApkDownloader {

    private static final String TAG = "ApkDownloader";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 60_000;
    private static final int BUFFER_SIZE        = 8192;

    public interface Callback {
        void onProgress(int percent);
        void onSuccess(File apkFile, String actualSha256);
        void onFailure(String reason);
    }

    private final Context context;

    public ApkDownloader(Context context) {
        this.context = context;
    }

    /**
     * APK를 다운로드하고 sha256을 검증한 뒤 live 경로로 원자적 이동.
     * 호출 측에서 백그라운드 스레드 보장 필요.
     *
     * @param apkUrl       다운로드 URL
     * @param expectedSha256 manifest에서 가져온 예상 sha256 (16진수 소문자)
     * @param callback     결과 콜백
     */
    public void download(String apkUrl, String expectedSha256, Callback callback) {
        File cacheDir = context.getCacheDir();
        File tmpFile  = new File(cacheDir, "launcher_update.apk.tmp");
        File liveFile = new File(cacheDir, "launcher_update.apk");

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apkUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                callback.onFailure("HTTP " + responseCode);
                return;
            }

            long contentLength = conn.getContentLengthLong();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long downloaded = 0;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmpFile)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    digest.update(buf, 0, read);
                    downloaded += read;
                    if (contentLength > 0) {
                        callback.onProgress((int) (downloaded * 100 / contentLength));
                    }
                }
            }

            String actualSha256 = toHex(digest.digest());
            Log.i(TAG, "다운로드 완료. sha256=" + actualSha256);

            if (expectedSha256 != null && !expectedSha256.equalsIgnoreCase(actualSha256)) {
                tmpFile.delete();
                callback.onFailure("sha256 불일치: expected=" + expectedSha256 + " actual=" + actualSha256);
                return;
            }

            atomicMove(tmpFile, liveFile);
            Log.i(TAG, "ATOMIC_MOVE 완료: " + liveFile.getAbsolutePath());
            callback.onSuccess(liveFile, actualSha256);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Android 보장 알고리즘 — 도달 불가
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.e(TAG, "다운로드 실패", e);
            tmpFile.delete();
            callback.onFailure("IOException: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 원자적 파일 이동. tmp/live가 동일 파티션이면 ATOMIC_MOVE 사용,
     * 파티션 경계를 넘어야 하는 경우(AtomicMoveNotSupportedException) copy+delete로 폴백.
     */
    private void atomicMove(File src, File dst) throws IOException {
        try {
            Files.move(src.toPath(), dst.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Log.w(TAG, "ATOMIC_MOVE 불가 (파티션 경계?) — copy+delete 폴백");
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            src.delete();
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
