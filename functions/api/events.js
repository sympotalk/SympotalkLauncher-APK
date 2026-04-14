import { SUPABASE_URL, getSupabaseKey, buildResponseHeaders, handleOptions, jsonError } from './_shared.js';

/**
 * GET /api/events
 *   → Supabase RPC pad_get_active_events() 호출
 *   → 응답: events + 담당자(display_name) + agency + updated_at
 *   → 엣지 15초 캐시 (500대 태블릿 동시 요청 시 Supabase 1회만)
 *
 * RPC 함수는 SECURITY DEFINER 로 user_profiles RLS 우회하여 담당자 이름만 안전 노출.
 * (migration: pad_launcher_public_events_function)
 */
export async function onRequest(context) {
  const { request } = context;

  if (request.method === 'OPTIONS') return handleOptions();
  if (request.method !== 'GET') return jsonError(405, 'Method not allowed');

  const cacheKey = new Request(request.url, { method: 'GET' });
  const cache = caches.default;

  // 1) 엣지 캐시 조회
  let cached = await cache.match(cacheKey);
  if (cached) {
    const resp = new Response(cached.body, cached);
    resp.headers.set('X-Cache', 'HIT');
    return resp;
  }

  // 2) 캐시 MISS → Supabase RPC 호출 (POST /rest/v1/rpc/pad_get_active_events)
  const supabaseKey = getSupabaseKey(context.env);
  const rpcUrl = SUPABASE_URL + '/rest/v1/rpc/pad_get_active_events';

  let upstreamResp;
  try {
    upstreamResp = await fetch(rpcUrl, {
      method: 'POST',
      headers: {
        'apikey': supabaseKey,
        'Authorization': 'Bearer ' + supabaseKey,
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'User-Agent': 'SympotalkProxy/1.1'
      },
      body: '{}',
      cf: { cacheTtl: 0, cacheEverything: false }
    });
  } catch (err) {
    return jsonError(502, 'Upstream fetch failed: ' + err.message);
  }

  const body = await upstreamResp.text();
  const headers = buildResponseHeaders(upstreamResp.ok ? 15 : 0);
  const resp = new Response(body, { status: upstreamResp.status, headers });
  resp.headers.set('X-Cache', 'MISS');

  if (upstreamResp.ok) {
    context.waitUntil(cache.put(cacheKey, resp.clone()));
  }
  return resp;
}
