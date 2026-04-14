-- Sympotalk 런처 태블릿 앱 전용 공개 함수 (v1.0.23)
-- 적용 일자: 2026-04-15 via supabase apply_migration MCP
--
-- 목적:
--   - 태블릿 런처가 events 목록 + 담당자 이름(display_name) 을 anon 키로 조회 가능
--   - user_profiles 테이블 RLS 는 건드리지 않고 함수로만 우회
--   - 노출 필드: id, name, slug, status, start_date, end_date, agency, updated_at, manager_name
--   - 노출하지 않는 민감 필드: phone, email, company_name, role, user_id
--
-- 검증 방법:
--   SELECT * FROM public.pad_get_active_events() LIMIT 3;
--   SELECT has_function_privilege('anon', 'public.pad_get_active_events()', 'EXECUTE'); -- true
--
-- 롤백:
--   DROP FUNCTION IF EXISTS public.pad_get_active_events();

CREATE OR REPLACE FUNCTION public.pad_get_active_events()
RETURNS TABLE(
  id uuid,
  name text,
  slug text,
  status text,
  start_date date,
  end_date date,
  agency text,
  updated_at timestamptz,
  manager_name text
)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT
    e.id,
    e.name,
    e.slug,
    e.status::text,
    e.start_date,
    e.end_date,
    e.agency,
    e.updated_at,
    up.display_name::text AS manager_name
  FROM public.events e
  LEFT JOIN public.user_profiles up ON up.user_id = e.manager_user_id
  WHERE e.status IN ('active', 'draft')
  ORDER BY e.start_date ASC NULLS LAST;
$$;

GRANT EXECUTE ON FUNCTION public.pad_get_active_events() TO anon, authenticated;

COMMENT ON FUNCTION public.pad_get_active_events() IS
  'Sympotalk Launcher (tablet app) - returns active events with manager display name. Public read via anon.';
