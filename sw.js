/* Service Worker - Sympotalk 행사 런처
 * 역할: 앱 캐싱, 오프라인 지원, 장시간 실행 유지
 */
const CACHE_NAME = 'sympotalk-launcher-v4';
const STATIC_ASSETS = [
  '/index.html',
  '/manifest.json'
];

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

// fetch: Supabase API는 항상 네트워크, 정적파일은 캐시 우선
self.addEventListener('fetch', function(event) {
  var url = event.request.url;

  // Supabase API 요청: 항상 네트워크
  if (url.indexOf('supabase.co') !== -1) {
    event.respondWith(
      fetch(event.request).catch(function() {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      })
    );
    return;
  }

  // 정적 파일: 캐시 우선, 없으면 네트워크
  event.respondWith(
    caches.match(event.request).then(function(cached) {
      if (cached) return cached;
      return fetch(event.request).then(function(response) {
        if (response.ok && event.request.method === 'GET') {
          var clone = response.clone();
          caches.open(CACHE_NAME).then(function(cache) {
            cache.put(event.request, clone);
          });
        }
        return response;
      }).catch(function() {
        return new Response('오프라인 상태입니다', { status: 503 });
      });
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
