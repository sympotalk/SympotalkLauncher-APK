import { SUPABASE_URL, proxyWithCache, handleOptions, jsonError } from './_shared.js';

/**
 * GET /api/rooms?event_id={uuid}            — 단일 행사의 룸 목록 (legacy)
 * GET /api/rooms?event_ids=uuid1,uuid2,...  — N개 행사의 룸을 1회 호출로 batch (perf/launcher-boot-burst)
 *   → 엣지 30초 캐시 + Supabase REST `event_id=in.(...)` 활용
 *   → event_ids 는 최대 50개 (Cloudflare URL 길이/Supabase IN 절 가독성 고려)
 *   → 모든 UUID 검증으로 악의적 쿼리 차단
 *
 * 102대 태블릿 동시 부팅 burst 완화 목적:
 *   - 기존: 이벤트 N개 × 102대 = N*102 동시 요청 (행사장 AP 큐잉 유발)
 *   - 변경: 1 × 102대 = 102 요청 (이벤트 카운트와 무관)
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_BATCH_IDS = 50;

export async function onRequest(context) {
  const { request } = context;

  if (request.method === 'OPTIONS') return handleOptions();
  if (request.method !== 'GET') return jsonError(405, 'Method not allowed');

  const url = new URL(request.url);
  const eventId = url.searchParams.get('event_id');
  const eventIdsRaw = url.searchParams.get('event_ids');

  /* batch 모드 우선 */
  if (eventIdsRaw) {
    const ids = eventIdsRaw.split(',').map(function(s) { return s.trim(); }).filter(Boolean);
    if (ids.length === 0) return jsonError(400, 'event_ids must not be empty');
    if (ids.length > MAX_BATCH_IDS) return jsonError(400, 'event_ids exceeds max ' + MAX_BATCH_IDS);
    for (let i = 0; i < ids.length; i++) {
      if (!UUID_RE.test(ids[i])) return jsonError(400, 'invalid uuid: ' + ids[i]);
    }
    const upstream = new URL(SUPABASE_URL + '/rest/v1/rooms');
    upstream.searchParams.set('select', 'id,name,slug,chair_token,console_token,moderator_token,event_id');
    upstream.searchParams.set('event_id', 'in.(' + ids.join(',') + ')');
    upstream.searchParams.set('order', 'sort_order.asc.nullslast');
    return proxyWithCache(context, upstream.toString(), 30);
  }

  /* 단일 모드 (기존 호환) */
  if (!eventId) return jsonError(400, 'event_id or event_ids parameter required');
  if (!UUID_RE.test(eventId)) return jsonError(400, 'event_id must be UUID');

  const upstream = new URL(SUPABASE_URL + '/rest/v1/rooms');
  upstream.searchParams.set('select', 'id,name,slug,chair_token,console_token,moderator_token');
  upstream.searchParams.set('event_id', 'eq.' + eventId);
  upstream.searchParams.set('order', 'sort_order.asc.nullslast');

  return proxyWithCache(context, upstream.toString(), 30);
}
