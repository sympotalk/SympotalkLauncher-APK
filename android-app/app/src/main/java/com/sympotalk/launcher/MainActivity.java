package com.sympotalk.launcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Toast;

import java.net.InetAddress;

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
    private static final int REQ_STARTUP = 1003;
    private boolean hasEverRequestedLocation = false;   // 최초 요청 플래그
    private volatile boolean pendingLongPressReturn = false;   // Long-press BACK → 행사상세 복귀용
                                                               // volatile: main thread(onKeyLongPress)와
                                                               //          JS bridge thread(consumeLongPressReturn) 간 가시성 확보

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── 화면 항상 켜짐 (네이티브 레벨, JS WakeLock보다 강력) ──
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        // 전체화면 몰입 모드 (applyImmersiveFlags 와 동일 플래그)
        // LAYOUT_FULLSCREEN 미포함: adjustResize 가 실제 viewport 를 축소하게 만듦

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
        applyImmersiveFlags();             // 초기 Immersive 플래그 (LAYOUT_FULLSCREEN 제외)
        setupKeyboardImmersiveHandler();   // 키보드 닫히면 전체화면 모드 복구
        checkStartupPermissions();
        checkForUpdates();
    }

    /**
     * 가상키보드가 닫힐 때 Immersive(전체화면) 모드가 자동 복구되지 않는 문제 대응.
     * ViewTreeObserver 로 visible frame 높이를 감시 → 키보드가 접히면 Immersive 재적용.
     */
    private void setupKeyboardImmersiveHandler() {
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            decor.getWindowVisibleDisplayFrame(r);
            int rootHeight = decor.getRootView().getHeight();
            int visibleHeight = r.bottom - r.top;
            // visibleHeight < rootHeight * 0.85 이면 키보드가 열린 것으로 간주
            // 반대로 0.85 이상이면 키보드 닫힘 → Immersive 재적용
            if (rootHeight > 0 && (double) visibleHeight / rootHeight >= 0.85) {
                applyImmersiveFlags();
            }
        });
    }

    /**
     * 자체 런처 도메인 여부 (host 기반 정확 매칭).
     * - BuildConfig.CLOUDFLARE_URL host (예: sympotalklauncher-apk.pages.dev) → false
     * - file:// 로컬 asset → false
     * - 그 외 https → true (외부 페이지)
     */
    private boolean isExternalUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("file://")) return false;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            android.net.Uri proxyUri = android.net.Uri.parse(BuildConfig.CLOUDFLARE_URL);
            String proxyHost = proxyUri.getHost();
            return !host.equalsIgnoreCase(proxyHost);
        } catch (Exception e) {
            return true;   // 파싱 실패 시 보수적으로 external 처리
        }
    }

    /**
     * 외부 웹페이지(sympopad.com 등)에 키보드 보정 JS 를 주입.
     * 해당 페이지가 자체 keyboard handling 을 안 해도 입력칸이 가상키보드 위에 보이도록 scrollIntoView.
     * setJavaScriptEnabled=true 필수 (이미 설정됨).
     */
    private void injectKeyboardHelper(WebView view) {
        // 한 번만 바인딩되게 global flag 체크 + 지연 스크롤
        String js =
            "(function(){" +
            "  if(window.__sympotalkKbHelper) return;" +
            "  window.__sympotalkKbHelper = true;" +
            "  var nonTextTypes = ['button','submit','reset','checkbox','radio','file'," +
            "    'date','time','datetime-local','month','week','color','range','hidden','image'];" +
            "  function isEditable(el){" +
            "    if(!el) return false;" +
            "    var t = el.tagName;" +
            "    if(t === 'TEXTAREA') return true;" +
            "    if(t === 'INPUT'){" +
            "      var ty = (el.type||'text').toLowerCase();" +
            "      return nonTextTypes.indexOf(ty) === -1;" +
            "    }" +
            "    return el.isContentEditable === true;" +
            "  }" +
            "  document.addEventListener('focusin', function(e){" +
            "    if(!isEditable(e.target)) return;" +
            "    setTimeout(function(){" +
            "      try { e.target.scrollIntoView({block:'center', behavior:'smooth'}); }" +
            "      catch(ex) { try { e.target.scrollIntoView(); } catch(_){} }" +
            "    }, 400);" +
            "  }, true);" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    /**
     * Immersive Sticky + Fullscreen + Hide Navigation
     * ⚠️ LAYOUT_FULLSCREEN / LAYOUT_HIDE_NAVIGATION 는 **의도적으로 제외**:
     *   이 플래그가 켜지면 레이아웃이 전체 화면으로 고정되어
     *   windowSoftInputMode=adjustResize 가 실제 viewport 를 축소하지 못함
     *   → 가상키보드가 입력창을 덮는 증상 발생
     */
    private void applyImmersiveFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN       |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    /**
     * 앱 시작 시 필수 권한 일괄 요청
     * - CAMERA: QR 스캐너
     * - ACCESS_FINE_LOCATION: WiFi 스캔 + WiFiInfo.getSSID (Android 8.1+ 필수)
     */
    private void checkStartupPermissions() {
        java.util.List<String> toRequest = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!toRequest.isEmpty()) {
            if (toRequest.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                hasEverRequestedLocation = true;
            }
            ActivityCompat.requestPermissions(this,
                toRequest.toArray(new String[0]), REQ_STARTUP);
        }
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
                // 로드 후 몰입 모드 재적용
                applyImmersiveFlags();

                // 외부 웹페이지(sympopad.com 등)에 키보드 보정 JS 주입 (백업)
                if (isExternalUrl(url)) {
                    injectKeyboardHelper(view);
                }
                // windowSoftInputMode 는 항상 ADJUST_RESIZE 로 통일
                //   - 외부 페이지: viewport 가 키보드 위로 축소 → 입력창 자연스레 키보드 위에 보임
                //   - 자체 페이지: bottomnav 는 body:height:100% 로 접혀도 OK
                getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
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
                    // 웹 콘텐츠만 갱신된 것. "APK 업데이트" 와 구분 위해 별도 문구.
                    // 사용자 혼란 방지: 토스트는 표시하되 짧고 명시적으로.
                    Toast.makeText(MainActivity.this,
                        "웹 콘텐츠 갱신됨 (v" + ver + ")", Toast.LENGTH_SHORT).show();
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

    // checkCameraPermission 은 checkStartupPermissions 로 통합됨

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);

        // 카메라 권한 — WebView 카메라 요청 응답용
        if (code == REQ_CAMERA && pendingPermission != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(pendingPermission.getResources());
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
            return;
        }

        // 위치 권한 — WiFi SSID 조회 + 스캔 재시도 + 현재 WiFi 상태 갱신
        if (code == REQ_LOCATION || code == REQ_STARTUP) {
            boolean locationGranted = false;
            for (int i = 0; i < perms.length && i < results.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(perms[i])
                        && results[i] == PackageManager.PERMISSION_GRANTED) {
                    locationGranted = true;
                    break;
                }
            }
            if (locationGranted) {
                // 위치 권한 허용 → 현재 WiFi 표시 + 스캔 목록 갱신
                jsCallback("window.updateCurrentWifi && updateCurrentWifi();"
                    + "window.refreshWifiListUIFromCache && refreshWifiListUIFromCache();");
            } else if (code == REQ_LOCATION) {
                jsCallback("window.onWifiScanError && onWifiScanError('위치 권한이 거부되었습니다')");
            }
        }
    }

    // ── Back 버튼 동작 ─────────────────────────────────
    // Short press: 무시 (사용자가 실수로 앱/페이지 이탈하는 것 방지)
    // Long  press: 런처 재로드 + localStorage 기반 마지막 행사 상세로 이동

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();    // long press 추적 활성화
            return true;              // down 이벤트 consume
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            pendingLongPressReturn = true;
            // sympopad.com 등 외부 URL 에 있을 수 있으므로 런처 HTML 재로드
            // Toast 는 JS 쪽에 위임 — 행사 이력 유무에 따라 다른 메시지를 표시하기 위함
            runOnUiThread(this::loadApp);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Short press: 아무것도 안 함 (long press 는 이미 onKeyLongPress 에서 처리됨)
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // 명시적 no-op — onKeyDown/Up 이 이벤트를 완전히 consume
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

        /** 위치 권한 보유 여부 (WiFi SSID 조회 + 스캔 필수) */
        @JavascriptInterface
        public boolean hasLocationPermission() {
            return ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        /**
         * JS 에서 위치 권한 요청.
         * "다시 묻지 않음" 상태면 시스템 다이얼로그 대신 앱 설정 화면을 엶.
         */
        @JavascriptInterface
        public void requestLocationPermission() {
            runOnUiThread(() -> {
                boolean alreadyGranted = ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (alreadyGranted) return;

                boolean canShowDialog = ActivityCompat.shouldShowRequestPermissionRationale(
                    MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);

                if (canShowDialog || !hasEverRequestedLocation) {
                    hasEverRequestedLocation = true;
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
                } else {
                    // "다시 묻지 않음" 상태 → 앱 상세 설정으로 유도
                    Toast.makeText(MainActivity.this,
                        "설정 → 권한 → 위치에서 직접 허용해주세요", Toast.LENGTH_LONG).show();
                    try {
                        Intent intent = new Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
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
         * (일부 Android 기기는 WifiManager 호출에 main looper 를 요구할 수 있어 UI 스레드 래핑)
         */
        @JavascriptInterface
        public void connectWifi(final String ssid, final String password, final String security) {
            runOnUiThread(() -> wifiHelper.connectToNetwork(ssid, password, security,
                new WifiHelper.ConnectCallback() {
                    @Override
                    public void onConnectResult(boolean success, String message) {
                        jsCallback("window.onWifiConnectResult && onWifiConnectResult(" +
                            success + "," + jsonStringify(message) + ")");
                    }
                }));
        }

        // ══════════════════════ 내부망 Ping / Back 복귀 ══════════════════════

        /** 현재 연결된 WiFi 의 gateway IP (x.x.x.x). 연결 안 됐거나 에러면 "" */
        @JavascriptInterface
        public String getGatewayIp() {
            try {
                WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
                if (wm == null) return "";
                DhcpInfo dhcp = wm.getDhcpInfo();
                if (dhcp == null || dhcp.gateway == 0) return "";
                int gw = dhcp.gateway;
                return String.format("%d.%d.%d.%d",
                    gw & 0xff, (gw >> 8) & 0xff, (gw >> 16) & 0xff, (gw >> 24) & 0xff);
            } catch (Exception e) { return ""; }
        }

        /**
         * 내부망 gateway(공유기) 로 ping. InetAddress.isReachable 사용 (ICMP/TCP).
         * @return -1 실패, 아니면 왕복시간(ms)
         */
        @JavascriptInterface
        public int pingGateway(int timeoutMs) {
            try {
                WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
                if (wm == null) return -1;
                DhcpInfo dhcp = wm.getDhcpInfo();
                if (dhcp == null || dhcp.gateway == 0) return -1;
                int gw = dhcp.gateway;
                InetAddress addr = InetAddress.getByAddress(new byte[] {
                    (byte)(gw & 0xff), (byte)((gw >> 8) & 0xff),
                    (byte)((gw >> 16) & 0xff), (byte)((gw >> 24) & 0xff)
                });
                long start = System.currentTimeMillis();
                boolean ok = addr.isReachable(timeoutMs);
                long elapsed = System.currentTimeMillis() - start;
                return ok ? (int) elapsed : -1;
            } catch (Exception e) { return -1; }
        }

        /**
         * Back 버튼 Long-press 로 런처가 재로드된 경우, JS 초기화 시 이 메서드로 확인.
         * true 를 반환하면 JS 가 localStorage.last_event_detail 을 읽어 해당 행사로 이동.
         */
        @JavascriptInterface
        public boolean consumeLongPressReturn() {
            boolean was = pendingLongPressReturn;
            pendingLongPressReturn = false;
            return was;
        }

        // ══════════════════════ APK 자동 업데이트 ══════════════════════

        /** 현재 앱 버전 (네이티브) */
        @JavascriptInterface
        public String getAppVersion() { return BuildConfig.VERSION_NAME; }

        /**
         * 앱 삭제 인텐트 (서명 불일치로 업데이트 실패 시 사용).
         * 시스템이 "이 앱을 제거하시겠습니까?" 확인 다이얼로그를 표시.
         */
        @JavascriptInterface
        public void uninstallSelf() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:" + getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "삭제 화면을 열 수 없습니다: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
        }

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
        if (hasFocus) applyImmersiveFlags();
    }
}
