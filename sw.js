/* Service Worker - Sympotalk 행사 런처
 * 역할: 앱 캐싱, 오프라인 지원, 장시간 실행 유지
 *
 * 운영 주의 (perf/launcher-boot-burst):
 *   - CACHE_NAME 을 bump 하면 모든 태블릿이 다음 부팅에서 STATIC_ASSETS 를 다시 다운로드한다.
 *   - 102대 동시 cache-miss 가 행사 직전에 일어나면 AP burst 가 다시 발생.
 *   - 따라서 행사 시작 24h 이내에는 CACHE_NAME bump 금지. 운영 매뉴얼 참조.
 */
const CACHE_NAME = 'sympotalk-launcher-v6';
const STATIC_ASSETS = [
  '/index.html',
  '/manifest.json',
  /* jsQR self-host — head 동기 로드 제거 (외부 CDN 의존 제거) */
  '/vendor/jsQR.min.js'
];

/* network-first 요청에도 클라이언트 fetch 와 동일한 5초 timeout 적용 */
var NETWORK_TIMEOUT_MS = 5000;
function fetchWithTimeout(request, timeoutMs) {
  if (typeof AbortController !== 'function') {
    return fetch(request);
  }
  var ctrl = new AbortController();
  var timer = setTimeout(function() { ctrl.abort(); }, timeoutMs);
  return fetch(request, { signal: ctrl.signal }).then(function(r) {
    clearTimeout(timer);
    return r;
  }).catch(function(err) {
    clearTimeout(timer);
    throw err;
  });
}

// 설치: 정적 파일 캐시
self.addEventListener('install', function(event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache) {
      return cache.addAll(STATIC_ASSETS);
    }).then(function() {
      return self.skipWaiting();
    })
  );
});

// 활성화: 구버전 캐시 정리
self.addEventListener('activate', function(event) {
  event.waitUntil(
    Promise.all([
      self.clients.claim(),
      caches.keys().then(function(keys) {
        return Promise.all(
          keys
            .filter(function(key) { return key !== CACHE_NAME; })
            .map(function(key) { return caches.delete(key); })
        );
      })
    ])
  );
});

// fetch: Supabase API는 항상 네트워크(+timeout), 정적파일은 stale-while-revalidate
self.addEventListener('fetch', function(event) {
  var url = event.request.url;

  // Supabase API 요청: 항상 네트워크 + 5초 timeout (행사장 WiFi 큐잉 방지)
  if (url.indexOf('supabase.co') !== -1) {
    event.respondWith(
      fetchWithTimeout(event.request, NETWORK_TIMEOUT_MS).catch(function() {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      })
    );
    return;
  }

  // 정적 파일: stale-while-revalidate (캐시 즉시 응답 + 백그라운드 갱신)
  event.respondWith(
    caches.match(event.request).then(function(cached) {
      var networkFetch = fetch(event.request).then(function(response) {
        if (response.ok && event.request.method === 'GET') {
          var clone = response.clone();
          caches.open(CACHE_NAME).then(function(cache) {
            cache.put(event.request, clone);
          });
        }
        return response;
      }).catch(function() {
        if (cached) return cached;
        return new Response('오프라인 상태입니다', { status: 503 });
      });
      return cached || networkFetch;
    })
  );
});

// 백그라운드 sync (앱 keepalive 지원)
self.addEventListener('sync', function(event) {
  if (event.tag === 'keepalive') {
    event.waitUntil(Promise.resolve());
  }
});

// 주기적 백그라운드 sync (지원되는 브라우저용)
self.addEventListener('periodicsync', function(event) {
  if (event.tag === 'app-refresh') {
    event.waitUntil(Promise.resolve());
  }
});

// 클라이언트에서 메시지 수신 (keepalive ping)
self.addEventListener('message', function(event) {
  if (event.data === 'keepalive') {
    event.ports[0].postMessage('alive');
  }
});
