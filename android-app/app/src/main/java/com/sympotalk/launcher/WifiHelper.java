package com.sympotalk.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 9 (LG G Pad 5) 전용 WiFi 스캔/연결 헬퍼
 * - WifiManager.addNetwork() + enableNetwork() 레거시 API 사용
 * - Android 10+ 에서는 동작 제한될 수 있음 (타겟 기기는 Android 9)
 */
public class WifiHelper {
    private static final String TAG = "WifiHelper";

    public interface ScanCallback {
        void onScanComplete(String jsonResults);
        void onScanFailed(String reason);
    }

    public interface ConnectCallback {
        void onConnectResult(boolean success, String message);
    }

    private final Context context;
    private final WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;

    public WifiHelper(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public boolean enableWifi() {
        if (wifiManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 에서는 앱이 직접 WiFi 토글 불가 → 사용자가 시스템에서 켜야 함
            return false;
        }
        try {
            return wifiManager.setWifiEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "enableWifi 실패: " + e.getMessage());
            return false;
        }
    }

    /** 현재 연결된 WiFi SSID 반환 (없으면 null) */
    public String getCurrentSSID() {
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null) return null;
            String ssid = info.getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) return null;
            // 따옴표 제거
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            return ssid;
        } catch (Exception e) {
            return null;
        }
    }

    /** 주변 WiFi 네트워크 스캔 (비동기) */
    public void scanNetworks(final ScanCallback callback) {
        if (wifiManager == null) {
            callback.onScanFailed("WiFi 관리자 사용 불가");
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            callback.onScanFailed("WiFi가 꺼져 있습니다");
            return;
        }

        // 기존 리시버 정리
        unregisterScanReceiver();

        // 중복 콜백 방지용 플래그
        final AtomicBoolean delivered = new AtomicBoolean(false);

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!delivered.compareAndSet(false, true)) return; // 이미 전달됨
                try {
                    List<ScanResult> results = wifiManager.getScanResults();
                    callback.onScanComplete(scanResultsToJson(results));
                } catch (SecurityException se) {
                    callback.onScanFailed("위치 권한이 필요합니다: " + se.getMessage());
                } catch (Exception e) {
                    callback.onScanFailed("스캔 결과 조회 실패: " + e.getMessage());
                } finally {
                    unregisterScanReceiver();
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(scanReceiver, filter);

        boolean started;
        try {
            started = wifiManager.startScan();
        } catch (Exception e) {
            unregisterScanReceiver();
            if (delivered.compareAndSet(false, true)) {
                callback.onScanFailed("스캔 시작 실패: " + e.getMessage());
            }
            return;
        }

        // Android 9+ startScan 은 throttle 걸려 false 반환할 수 있음 → 캐시된 결과라도 반환
        if (!started) {
            try {
                List<ScanResult> cached = wifiManager.getScanResults();
                if (cached != null && !cached.isEmpty() && delivered.compareAndSet(false, true)) {
                    callback.onScanComplete(scanResultsToJson(cached));
                    unregisterScanReceiver();
                }
            } catch (Exception ignored) {}
        }
    }

    private void unregisterScanReceiver() {
        if (scanReceiver != null) {
            try { context.unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
            scanReceiver = null;
        }
    }

    private String scanResultsToJson(List<ScanResult> results) {
        JSONArray arr = new JSONArray();
        if (results == null) return arr.toString();

        // 중복 SSID 제거 (signal 강한 것만 유지)
        java.util.Map<String, ScanResult> uniq = new java.util.HashMap<>();
        for (ScanResult r : results) {
            if (r.SSID == null || r.SSID.isEmpty()) continue;
            ScanResult prev = uniq.get(r.SSID);
            if (prev == null || r.level > prev.level) uniq.put(r.SSID, r);
        }

        // 신호 강도 내림차순 정렬
        java.util.List<ScanResult> sorted = new java.util.ArrayList<>(uniq.values());
        java.util.Collections.sort(sorted, new java.util.Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) { return b.level - a.level; }
        });

        try {
            for (ScanResult r : sorted) {
                JSONObject o = new JSONObject();
                o.put("ssid", r.SSID);
                o.put("bssid", r.BSSID);
                o.put("level", r.level);            // dBm
                o.put("frequency", r.frequency);    // MHz
                o.put("capabilities", r.capabilities);
                o.put("security", detectSecurity(r.capabilities));
                // 신호 강도 0-4 단계
                o.put("signalBars", WifiManager.calculateSignalLevel(r.level, 5));
                arr.put(o);
            }
        } catch (Exception e) {
            Log.w(TAG, "JSON 변환 실패: " + e.getMessage());
        }
        return arr.toString();
    }

    private String detectSecurity(String capabilities) {
        if (capabilities == null) return "NONE";
        String c = capabilities.toUpperCase();
        if (c.contains("WPA3")) return "WPA3";
        if (c.contains("WPA2")) return "WPA2";
        if (c.contains("WPA"))  return "WPA";
        if (c.contains("WEP"))  return "WEP";
        if (c.contains("EAP"))  return "EAP";
        return "NONE";
    }

    /** WiFi 네트워크 연결 (Android 9 legacy API) */
    public void connectToNetwork(final String ssid, final String password, final String security,
                                 final ConnectCallback callback) {
        if (wifiManager == null) {
            callback.onConnectResult(false, "WiFi 관리자 사용 불가");
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            callback.onConnectResult(false, "WiFi를 먼저 켜주세요");
            return;
        }

        // Android 10+ 에서는 레거시 API 제한적 → 시스템 설정으로 유도
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callback.onConnectResult(false, "Android 10 이상에서는 시스템 WiFi 설정을 이용해주세요");
            return;
        }

        try {
            WifiConfiguration config = buildConfig(ssid, password, security);

            // 1) 이미 연결된 SSID 인 경우 바로 성공 반환
            String currentSsid = getCurrentSSID();
            if (currentSsid != null && currentSsid.equals(ssid)) {
                callback.onConnectResult(true, ssid + " 에 이미 연결됨");
                return;
            }

            // 2) 우리 앱이 과거에 저장한 프로필이 있는지 탐색 (Android 9 제한: 우리가 추가한 것만 반환)
            int existingId = -1;
            List<WifiConfiguration> existing = null;
            try { existing = wifiManager.getConfiguredNetworks(); } catch (Exception ignored) {}
            if (existing != null) {
                for (WifiConfiguration ex : existing) {
                    if (ex.SSID != null && ex.SSID.equals(config.SSID)) {
                        existingId = ex.networkId;
                        break;
                    }
                }
            }

            int networkId = -1;

            if (existingId != -1) {
                // 기존 프로필이 있으면 바로 enable 시도 (password 가 맞으면 성공)
                wifiManager.disconnect();
                boolean ok = wifiManager.enableNetwork(existingId, true);
                wifiManager.reconnect();
                if (ok) {
                    callback.onConnectResult(true, ssid + " (저장된 프로필) 연결 시도 중...");
                    return;
                }
                // enable 실패 → password 변경 가능성. update 시도
                config.networkId = existingId;
                networkId = wifiManager.updateNetwork(config);
                if (networkId == -1) {
                    // update 불가 → 제거 후 재추가
                    wifiManager.removeNetwork(existingId);
                    try { wifiManager.saveConfiguration(); } catch (Exception ignored) {}
                    config.networkId = -1;
                    networkId = wifiManager.addNetwork(config);
                }
            } else {
                // 우리 앱 프로필 없음 → 새로 추가 시도
                networkId = wifiManager.addNetwork(config);
            }

            // addNetwork 가 -1 이면 시스템 다른 앱(또는 시스템 설정)이 같은 SSID 를 소유 중
            if (networkId == -1) {
                callback.onConnectResult(false,
                    "SYSTEM_PROFILE_EXISTS|" + ssid);   // JS 가 파싱해서 시스템 설정 버튼 제공
                return;
            }

            wifiManager.disconnect();
            boolean enabled = wifiManager.enableNetwork(networkId, true);
            wifiManager.reconnect();
            try { wifiManager.saveConfiguration(); } catch (Exception ignored) {}

            if (enabled) {
                callback.onConnectResult(true, ssid + " 연결 시도 중...");
            } else {
                callback.onConnectResult(false, "네트워크 활성화 실패 (잠시 후 다시 시도하세요)");
            }
        } catch (Exception e) {
            callback.onConnectResult(false, "오류: " + e.getMessage());
        }
    }

    /** 완전한 WPA/WPA2/WEP/Open WifiConfiguration 빌드 */
    private WifiConfiguration buildConfig(String ssid, String password, String security) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.status = WifiConfiguration.Status.ENABLED;
        config.priority = 40;

        String sec = security == null ? "" : security.toUpperCase();

        if (sec.contains("WPA3")) {
            // WPA3 SAE - Android 9에선 SAE 미지원. Transition Mode AP 라면 WPA2 폴백 가능.
            // Pure WPA3-only AP는 Android 9 에서 연결 불가 (UI 레이어에서 조기 차단 권장)
            config.preSharedKey = "\"" + password + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        } else if (sec.contains("WPA")) {
            // WPA / WPA2 PSK - 완전한 알고리즘 세트 명시 (addNetwork -1 방지)
            config.preSharedKey = "\"" + password + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        } else if (sec.contains("WEP")) {
            // WEP - hex는 따옴표 없이, ASCII는 따옴표로 감싸기
            // allowedProtocols는 설정하지 않음 (AOSP 참조 구현에 맞춤)
            boolean isHex = password != null
                && (password.length() == 10 || password.length() == 26 || password.length() == 58)
                && password.matches("[0-9A-Fa-f]+");
            config.wepKeys[0] = isHex ? password : "\"" + password + "\"";
            config.wepTxKeyIndex = 0;
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        } else {
            // Open network
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return config;
    }
}
