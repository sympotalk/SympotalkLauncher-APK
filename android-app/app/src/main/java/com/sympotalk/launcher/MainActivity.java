package com.sympotalk.launcher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private WifiHelper wifiHelper;
    private AppUpdateManager appUpdateManager;
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_LOCATION = 1002;

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

        wifiHelper = new WifiHelper(this);
        appUpdateManager = new AppUpdateManager(this, BuildConfig.GITHUB_REPO);
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

        // 오프라인 캐시 (setAppCacheEnabled는 API 33부터 제거됨. HTTP 캐시가 자동 사용됨)
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 앱 식별 UA 추가 (BuildConfig 에서 동적으로 버전 참조)
        s.setUserAgentString(s.getUserAgentString() + " SympotalkLauncher/" + BuildConfig.VERSION_NAME);

        // ── JavaScript Bridge: JS에서 Android 기능 호출 가능 ──
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

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
            return;
        }
        if (code == REQ_LOCATION) {
            // 위치 권한 허용 시 WiFi 스캔 자동 재시도
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                jsCallback("window.refreshWifiList && refreshWifiList()");
            } else {
                jsCallback("window.onWifiScanError && onWifiScanError('위치 권한이 거부되었습니다')");
            }
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

    // ═════════════════════════════════════════════════════
    // JavaScript Bridge: window.AndroidBridge.xxx() 로 호출
    // ═════════════════════════════════════════════════════
    public class AndroidBridge {

        /** JS에서 Android WiFi 설정 화면 열기 */
        @JavascriptInterface
        public void openWifiSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "WiFi 설정을 열 수 없습니다: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                }
            });
        }

        /** JS에서 Android 전체 설정 화면 열기 */
        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }

        /** JS에서 환경 확인 */
        @JavascriptInterface
        public boolean isAndroidApp() { return true; }

        /** 앱 종료 (외부 브라우저로 전환 등) */
        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> moveTaskToBack(true));
        }

        /** 짧은 토스트 메시지 */
        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        // ══════════════════════ WiFi 기능 ══════════════════════

        /** WiFi 켜짐 여부 */
        @JavascriptInterface
        public boolean isWifiEnabled() {
            return wifiHelper != null && wifiHelper.isWifiEnabled();
        }

        /** WiFi 켜기 시도 (Android 10+ 불가) */
        @JavascriptInterface
        public boolean enableWifi() {
            return wifiHelper != null && wifiHelper.enableWifi();
        }

        /** 현재 연결된 WiFi SSID (없으면 빈 문자열) */
        @JavascriptInterface
        public String getCurrentSSID() {
            String s = wifiHelper == null ? null : wifiHelper.getCurrentSSID();
            return s == null ? "" : s;
        }

        /**
         * WiFi 스캔. 결과는 window.onWifiScanResult(jsonArrayString) 로 비동기 콜백
         * 실패 시 window.onWifiScanError(message)
         */
        @JavascriptInterface
        public void startWifiScan() {
            // 위치 권한 필수
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> {
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_LOCATION);
                    jsCallback("window.onWifiScanError && onWifiScanError('위치 권한을 허용하면 다시 시도해주세요')");
                });
                return;
            }

            wifiHelper.scanNetworks(new WifiHelper.ScanCallback() {
                @Override public void onScanComplete(String jsonResults) {
                    jsCallback("window.onWifiScanResult && onWifiScanResult(" +
                        jsonStringify(jsonResults) + ")");
                }
                @Override public void onScanFailed(String reason) {
                    jsCallback("window.onWifiScanError && onWifiScanError(" +
                        jsonStringify(reason) + ")");
                }
            });
        }

        /**
         * WiFi 네트워크 연결. 결과는 window.onWifiConnectResult(success, message) 로 콜백
         */
        @JavascriptInterface
        public void connectWifi(String ssid, String password, String security) {
            wifiHelper.connectToNetwork(ssid, password, security,
                new WifiHelper.ConnectCallback() {
                    @Override
                    public void onConnectResult(boolean success, String message) {
                        jsCallback("window.onWifiConnectResult && onWifiConnectResult(" +
                            success + "," + jsonStringify(message) + ")");
                    }
                });
        }

        // ══════════════════════ APK 자동 업데이트 ══════════════════════

        /** 현재 앱 버전 (네이티브) */
        @JavascriptInterface
        public String getAppVersion() { return BuildConfig.VERSION_NAME; }

        /**
         * GitHub Releases 에서 최신 APK 확인.
         * 결과: window.onAppUpdateResult(status, latestVersion, apkUrl, notes)
         *   status: "available" | "uptodate" | "error"
         */
        @JavascriptInterface
        public void checkAppUpdate() {
            appUpdateManager.checkForUpdate(new AppUpdateManager.CheckCallback() {
                @Override public void onUpdateAvailable(String latestVersion, String apkUrl, String notes) {
                    jsCallback("window.onAppUpdateResult && onAppUpdateResult('available'," +
                        jsonStringify(latestVersion) + "," + jsonStringify(apkUrl) + "," + jsonStringify(notes) + ")");
                }
                @Override public void onUpToDate(String currentVersion) {
                    jsCallback("window.onAppUpdateResult && onAppUpdateResult('uptodate'," +
                        jsonStringify(currentVersion) + ",null,null)");
                }
                @Override public void onCheckFailed(String reason) {
                    jsCallback("window.onAppUpdateResult && onAppUpdateResult('error',null,null," +
                        jsonStringify(reason) + ")");
                }
            });
        }

        /**
         * APK 다운로드 + 설치 인텐트 실행
         * 진행: window.onAppDownloadProgress(percent)
         * 완료: window.onAppDownloadComplete() → 자동으로 설치 인텐트 실행
         * 실패: window.onAppDownloadError(message)
         */
        @JavascriptInterface
        public void downloadAndInstallApk(String apkUrl) {
            // 알 수 없는 출처 허용 확인 (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "설정에서 '알 수 없는 출처 설치 허용'을 켜주세요",
                        Toast.LENGTH_LONG).show();
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                });
                jsCallback("window.onAppDownloadError && onAppDownloadError('설치 권한을 허용한 후 다시 시도해주세요')");
                return;
            }

            appUpdateManager.downloadApk(apkUrl, new AppUpdateManager.DownloadCallback() {
                @Override public void onProgress(int percent) {
                    jsCallback("window.onAppDownloadProgress && onAppDownloadProgress(" + percent + ")");
                }
                @Override public void onDownloadComplete(java.io.File apkFile) {
                    jsCallback("window.onAppDownloadComplete && onAppDownloadComplete()");
                    runOnUiThread(() -> appUpdateManager.installApk(apkFile));
                }
                @Override public void onDownloadFailed(String reason) {
                    jsCallback("window.onAppDownloadError && onAppDownloadError(" + jsonStringify(reason) + ")");
                }
            });
        }
    }

    // ─── JS 콜백 헬퍼 ───
    private void jsCallback(final String jsSnippet) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(jsSnippet, null);
        });
    }

    /** JS string literal 로 안전하게 인코딩 (quoted) */
    private static String jsonStringify(String s) {
        if (s == null) return "null";
        // JSON string 이미 배열/객체면 그대로 사용
        String trimmed = s.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) return s;
        // 아니면 문자열로 quote
        return org.json.JSONObject.quote(s);
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
