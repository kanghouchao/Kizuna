'use client';

import { useEffect, useRef, useState } from 'react';
import { ResourceSuccess } from './useKeyedResource';

/** 同じ成功結果の再描画では入力を保持し、新しい結果だけを同期初期化する。 */
export function useResourceInitialization<T>(
  success: ResourceSuccess<T> | null,
  initialize: (data: T) => void
): boolean {
  const initializeRef = useRef(initialize);
  const [initialized, setInitialized] = useState<ResourceSuccess<T> | null>(null);
  useEffect(() => {
    initializeRef.current = initialize;
  });
  useEffect(() => {
    if (success === null) return;
    initializeRef.current(success.data);
    setInitialized(success);
  }, [success]);
  return success !== null && initialized === success;
}
