'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createResourceRequest, ResourceFailure, ResourceLoad } from './resourceRequest';

export type ResourceKey = readonly (string | number | boolean | null | undefined)[];
export interface ResourceSuccess<T> {
  key: ResourceKey;
  data: T;
}

export interface KeyedResource<T> {
  data: T | null;
  success: ResourceSuccess<T> | null;
  retained: ResourceSuccess<T> | null;
  isLoading: boolean;
  failure: ResourceFailure | null;
  reload: () => Promise<ResourceLoad<T>>;
  capture: () => { isCurrent: () => boolean; replace: (data: T) => boolean };
}

function equal(a: readonly unknown[], b: readonly unknown[]) {
  return a.length === b.length && a.every((value, i) => Object.is(value, b[i]));
}

/** キーはプリミティブの列を値比較する。条件だけの変更では同じ対象の保持値を利用できる。 */
export function useKeyedResource<T>(
  key: ResourceKey,
  fetcher: (() => Promise<T>) | null,
  deps: readonly unknown[] = []
): KeyedResource<T> {
  const enabled = fetcher !== null;
  const [scope, setScope] = useState(() => ({ key, enabled, request: createResourceRequest() }));
  const [conditions, setConditions] = useState(deps);
  if (!equal(scope.key, key) || scope.enabled !== enabled) {
    setScope({ key, enabled, request: createResourceRequest() });
  }
  if (!equal(conditions, deps)) setConditions(deps);
  const [state, setState] = useState<{
    scope: typeof scope | null;
    successScope: typeof scope | null;
    conditions: readonly unknown[];
    retained: ResourceSuccess<T> | null;
    isLoading: boolean;
    failure: ResourceFailure | null;
  }>({
    scope: null,
    successScope: null,
    conditions,
    retained: null,
    isLoading: enabled,
    failure: null,
  });
  const fetcherRef = useRef(fetcher);
  useLayoutEffect(() => {
    fetcherRef.current = fetcher;
  });
  const currentRef = useRef<{ scope: typeof scope; conditions: readonly unknown[] } | null>(null);
  useLayoutEffect(() => {
    currentRef.current = { scope, conditions };
    scope.request.invalidate();
    return () => {
      currentRef.current = null;
      scope.request.invalidate();
    };
  }, [scope, conditions]);

  const reload = useCallback(async (): Promise<ResourceLoad<T>> => {
    if (
      currentRef.current?.scope !== scope ||
      currentRef.current.conditions !== conditions ||
      !scope.enabled ||
      fetcherRef.current === null
    )
      return { status: 'stale' };
    setState(previous => ({ ...previous, scope, conditions, isLoading: true, failure: null }));
    const result = await scope.request.run(fetcherRef.current);
    if (result.status === 'stale' || currentRef.current?.scope !== scope || !result.isCurrent())
      return { status: 'stale' };
    setState({
      scope,
      successScope: scope,
      conditions,
      retained: result.status === 'success' ? { key: scope.key, data: result.data } : null,
      isLoading: false,
      failure: result.status === 'success' ? null : result.status,
    });
    return result;
  }, [scope, conditions]);
  useEffect(() => {
    void reload();
  }, [reload]);

  const capture = useCallback(() => {
    const requestCurrent = scope.request.capture();
    const isCurrent = () =>
      currentRef.current?.scope === scope &&
      currentRef.current.conditions === conditions &&
      scope.enabled &&
      requestCurrent();
    return {
      isCurrent,
      replace: (data: T) => {
        if (!isCurrent() || data == null) return false;
        scope.request.invalidate();
        setState({
          scope,
          successScope: scope,
          conditions,
          retained: { key: scope.key, data },
          isLoading: false,
          failure: null,
        });
        return true;
      },
    };
  }, [scope, conditions]);
  const matches = enabled && scope.enabled && state.scope === scope;
  const pending = !matches || !equal(state.conditions, conditions);
  const success =
    matches && state.successScope === scope && state.retained && equal(state.retained.key, key)
      ? state.retained
      : null;
  return {
    data: success?.data ?? null,
    success,
    retained: state.retained,
    isLoading: enabled && (pending || state.isLoading),
    failure: pending ? null : state.failure,
    reload,
    capture,
  };
}
