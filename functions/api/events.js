import { SUPABASE_URL, proxyWithCache, handleOptions, jsonError } from './_shared.js';

/**
 * GET /api/events
 *   → Supabase /rest/v1/events 프록시
 *   → 엣지 15초 캐시 (500대 태블릿 동시 요청 시 Supabase 1회만 호출)
 */
export async function onRequest(context) {
  const { request } = context;

  if (request.method === 'OPTIONS') return handleOptions();
  if (request.method !== 'GET') return jsonError(405, 'Method not allowed');

  // Supabase 쿼리 조립 — index.html 의 기존 쿼리와 동일
  const upstream = new URL(SUPABASE_URL + '/rest/v1/events');
  upstream.searchParams.set('select', 'id,name,slug,status,start_date,end_date,agency');
  upstream.searchParams.set('status', 'in.(active,draft)');
  upstream.searchParams.set('order', 'start_date.asc.nullslast');

  return proxyWithCache(context, upstream.toString(), 15);  // 15초 엣지 캐시
}
