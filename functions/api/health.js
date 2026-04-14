/**
 * GET /api/health
 *   → 프록시 동작 확인용 간단 엔드포인트
 *   → 응답: { ok: true, ts: ISO8601, cache: "MISS|HIT" }
 */
export async function onRequest(context) {
  return new Response(
    JSON.stringify({
      ok: true,
      ts: new Date().toISOString(),
      worker: 'sympopad-proxy',
      version: '1.0'
    }, null, 2),
    {
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': '*'
      }
    }
  );
}
