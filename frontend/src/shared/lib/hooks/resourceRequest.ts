import { isNotFound } from '../apiError';

export type ResourceFailure = 'notFound' | 'error';
export type ResourceLoad<T> =
  | { status: 'success'; data: T; isCurrent: () => boolean }
  | { status: ResourceFailure; isCurrent: () => boolean }
  | { status: 'stale' };

/** 成功・失敗の双方を同じ取得順で裁定する。 */
export function createResourceRequest() {
  let generation = 0;
  const invalidate = () => {
    generation++;
  };
  const capture = () => {
    const expected = generation;
    return () => generation === expected;
  };
  const run = async <T>(fetcher: () => Promise<T>): Promise<ResourceLoad<T>> => {
    invalidate();
    const isCurrent = capture();
    try {
      const data = await fetcher();
      if (!isCurrent()) return { status: 'stale' };
      return data == null ? { status: 'error', isCurrent } : { status: 'success', data, isCurrent };
    } catch (error) {
      if (!isCurrent()) return { status: 'stale' };
      return { status: isNotFound(error) ? 'notFound' : 'error', isCurrent };
    }
  };
  return { invalidate, capture, run };
}
