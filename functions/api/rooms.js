import { SUPABASE_URL, proxyWithCache, handleOptions, jsonError } from './_shared.js';

/**
 * GET /api/rooms?event_id={uuid}
 *   → Supabase /rest/v1/rooms 프록시 (특정 행사의 룸 목록)
 *   → 엣지 30초 캐시 (룸은 드물게 변경됨)
 *   → event_id UUID 검증으로 악의적 쿼리 파라미터 차단
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function onRequest(context) {
  const { request } = context;

  if (request.method === 'OPTIONS') return handleOptions();
  if (request.method !== 'GET') return jsonError(405, 'Method not allowed');

  const url = new URL(request.url);
  const eventId = url.searchParams.get('event_id');

  if (!eventId) return jsonError(400, 'event_id parameter required');
  if (!UUID_RE.test(eventId)) return jsonError(400, 'event_id must be UUID');

  const upstream = new URL(SUPABASE_URL + '/rest/v1/rooms');
  upstream.searchParams.set('select', 'id,name,slug,chair_token,console_token,moderator_token');
  upstream.searchParams.set('event_id', 'eq.' + eventId);
  upstream.searchParams.set('order', 'sort_order.asc.nullslast');

  return proxyWithCache(context, upstream.toString(), 30);  // 30초 엣지 캐시
}
