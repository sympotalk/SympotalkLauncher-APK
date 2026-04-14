package com.sympotalk.launcher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private PermissionRequest pendingPermission;
    private static final int REQ_CAMERA = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── 화면 항상 켜짐 (네이티브 레벨, JS WakeLock보다 강력) ──
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        // 전체화면 몰입 모드
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY     |
            View.SYSTEM_UI_FLAG_FULLSCREEN           |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION      |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE        |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN    |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        // 상태바/내비바 배경색
        getWindow().setStatusBarColor(Color.parseColor("#0f172a"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f172a"));

        // WebView를 직접 contentView로 설정
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0f172a"));
        setContentView(webView);

        setupWebView();
        checkCameraPermission();
        checkForUpdates();
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // file:// → Supabase HTTPS 요청 허용
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // HTTPS + HTTP 혼용 허용 (Supabase CDN)
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // NoSleep 비디오 자동 재생 허용
        s.setMediaPlaybackRequiresUserGesture(false);

        // 반응형 뷰포트
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        // 줌 비활성화 (태블릿 고정 레이아웃)
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        // 오프라인 캐시
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAppCacheEnabled(true);

        // 앱 식별 UA 추가
        s.setUserAgentString(s.getUserAgentString() + " SympotalkLauncher/1.0");

        // ── WebChromeClient: 카메라 권한 처리 ──
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean hasCamera = false;
                for (String res : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                        hasCamera = true;
                        break;
                    }
                }
                if (!hasCamera) { request.deny(); return; }

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    request.grant(request.getResources());
                } else {
                    pendingPermission = request;
                    ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        REQ_CAMERA
                    );
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                // WebView 콘솔 메시지를 Android logcat으로 전달 (디버깅용)
                android.util.Log.d("SympotalkJS", msg.sourceId() + ":" + msg.lineNumber() + " " + msg.message());
                return true;
            }
        });

        // ── WebViewClient: 내부 링크는 WebView에서, 외부 앱 링크는 시스템으로 ──
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("file://") || url.startsWith("javascript:")) {
                    return false; // WebView에서 처리
                }
                // tel:, mailto:, intent: 등 외부 앱으로
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 로드 후 몰입 모드 재적용 (일부 페이지 전환 후 UI가 보일 수 있음)
                view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY     |
                    View.SYSTEM_UI_FLAG_FULLSCREEN           |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                );
            }
        });

        loadApp();
    }

    private void loadApp() {
        UpdateManager um = new UpdateManager(this, BuildConfig.CLOUDFLARE_URL);
        File updatedFile = um.getUpdatedIndexFile();
        if (updatedFile != null) {
            // 다운로드된 최신 버전 로드
            webView.loadUrl("file://" + updatedFile.getAbsolutePath());
        } else {
            // 앱 번들 기본 버전 로드
            webView.loadUrl("file:///android_asset/www/index.html");
        }
    }

    private void checkForUpdates() {
        UpdateManager um = new UpdateManager(this, BuildConfig.CLOUDFLARE_URL);
        um.checkAndUpdate(new UpdateManager.Callback() {
            @Override public void onUpdateAvailable(String ver) {
                android.util.Log.i("Sympotalk", "업데이트 발견: " + ver);
            }
            @Override public void onUpdateComplete(String ver) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "업데이트 완료 (" + ver + ") - 재시작하면 적용됩니다", Toast.LENGTH_LONG).show();
                    // 바로 적용: 최신 파일로 reload
                    File f = new UpdateManager(MainActivity.this, BuildConfig.CLOUDFLARE_URL)
                        .getUpdatedIndexFile();
                    if (f != null) webView.loadUrl("file://" + f.getAbsolutePath());
                });
            }
            @Override public void onUpdateFailed(Exception e) {
                android.util.Log.w("Sympotalk", "업데이트 실패: " + e.getMessage());
            }
            @Override public void onUpToDate(String ver) {
                android.util.Log.d("Sympotalk", "최신 버전 (" + ver + ")");
            }
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAMERA && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(pendingPermission.getResources());
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            // 앱 종료 대신 백그라운드로 이동
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        // 복귀 시 화면 켜짐 재적용
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 포커스 복귀 시 몰입 모드 유지
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY     |
                View.SYSTEM_UI_FLAG_FULLSCREEN           |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION      |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE        |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }
}
