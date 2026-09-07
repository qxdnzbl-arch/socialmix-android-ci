-- Run before accepting concurrent bridge writes. Existing state keys are retained.
BEGIN;
ALTER TABLE public.agent_state ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION public.agent_state_bump_revision()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog AS $$
BEGIN
  NEW.revision := OLD.revision + 1;
  NEW.updated_at := clock_timestamp();
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.agent_state_bump_revision() FROM PUBLIC;
DROP TRIGGER IF EXISTS agent_state_revision ON public.agent_state;
CREATE TRIGGER agent_state_revision BEFORE UPDATE ON public.agent_state
FOR EACH ROW EXECUTE FUNCTION public.agent_state_bump_revision();

CREATE OR REPLACE FUNCTION public.agent_state_get(p_secret text, p_id text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE v jsonb; r bigint;
BEGIN
  IF NOT public.agent_check_secret(p_secret) THEN RAISE EXCEPTION 'unauthorized'; END IF;
  SELECT value, revision INTO v, r FROM public.agent_state WHERE id = p_id;
  RETURN coalesce(v, '{}'::jsonb) || jsonb_build_object('_store_revision',coalesce(r,0));
END;
$$;

CREATE OR REPLACE FUNCTION public.agent_state_set(p_secret text, p_id text, p_value jsonb)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE expected bigint; affected integer;
BEGIN
  IF NOT public.agent_check_secret(p_secret) THEN RAISE EXCEPTION 'unauthorized'; END IF;
  IF NOT (p_value ? '_store_revision') THEN
    RAISE SQLSTATE 'PT409' USING MESSAGE = 'state_revision_required';
  END IF;
  expected := (p_value->>'_store_revision')::bigint;
  UPDATE public.agent_state SET value = p_value - '_store_revision'
  WHERE id = p_id AND revision = expected;
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN
    RAISE SQLSTATE 'PT409' USING MESSAGE = 'state_changed_reload_required';
  END IF;
END;
$$;
COMMIT;
