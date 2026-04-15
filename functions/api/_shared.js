/**
 * 공통 설정 및 유틸 (Pages Functions)
 * - SUPABASE_KEY 는 anon 키라 노출돼도 RLS 가 보호. 환경변수 우선.
 */

export const SUPABASE_URL = 'https://bpxqnfwuvhlaottcnpie.supabase.co';

// 환경변수 우선, 없으면 하드코딩된 anon 키 사용
export function getSupabaseKey(env) {
  return (env && env.SUPABASE_ANON_KEY)
    || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJweHFuZnd1dmhsYW90dGNucGllIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc2OTYxMzMsImV4cCI6MjA4MzI3MjEzM30.F6pA27vkaAJFjK54vkYPbOI7K1LE2tQFVaPG-bA8r3c';
}

/** 공통 CORS + 캐시 헤더 */
export function buildResponseHeaders(extraCacheSeconds = 15) {
  return {
    'Content-Type': 'application/json; charset=utf-8',
    // s-maxage: Cloudflare 엣지 캐시
    // max-age:  브라우저/WebView 캐시 (기기 내)
    // stale-while-revalidate: TTL 지나도 백그라운드 revalidate 중 stale 제공
    'Cache-Control': `public, s-maxage=${extraCacheSeconds}, max-age=${extraCacheSeconds}, stale-while-revalidate=60`,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    // JS 에서 캐시 상태·POP 확인 가능하도록 노출
    'Access-Control-Expose-Headers': 'CF-Ray, X-Cache, X-Proxy-Version',
    'X-Proxy-Version': '1.0'
  };
}

export function handleOptions() {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Max-Age': '86400'
    }
  });
}

export function jsonError(status, message) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*'
    }
  });
}

/**
 * Supabase REST API 에 요청 + 엣지 캐시 적용
 *  - GET 요청만 캐시
 *  - X-Cache 헤더로 HIT/MISS 구분
 */
export async function proxyWithCache(context, upstreamUrl, cacheSeconds = 15) {
  const { request } = context;
  const supabaseKey = getSupabaseKey(context.env);

  const cacheKey = new Request(request.url, { method: 'GET' });
  const cache = caches.default;

  // 1) 엣지 캐시 조회
  let cached = await cache.match(cacheKey);
  if (cached) {
    const resp = new Response(cached.body, cached);
    resp.headers.set('X-Cache', 'HIT');
    return resp;
  }

  // 2) 캐시 MISS → Supabase 호출
  let upstreamResp;
  try {
    upstreamResp = await fetch(upstreamUrl, {
      method: 'GET',
      headers: {
        'apikey': supabaseKey,
        'Authorization': 'Bearer ' + supabaseKey,
        'Accept': 'application/json',
        'User-Agent': 'SympotalkProxy/1.0 (Cloudflare Workers)'
      },
      // Cloudflare-specific: upstream 실패 시 엣지 재시도 억제
      cf: { cacheTtl: 0, cacheEverything: false }
    });
  } catch (err) {
    return jsonError(502, 'Upstream fetch failed: ' + err.message);
  }

  const body = await upstreamResp.text();

  // upstream 에러 응답은 캐시하지 않음 (짧게 보관)
  const headers = buildResponseHeaders(
    upstreamResp.ok ? cacheSeconds : 0
  );
  const resp = new Response(body, {
    status: upstreamResp.status,
    headers
  });
  resp.headers.set('X-Cache', 'MISS');

  // 3) 200 만 캐시
  if (upstreamResp.ok) {
    context.waitUntil(cache.put(cacheKey, resp.clone()));
  }

  return resp;
}
