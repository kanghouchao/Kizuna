'use client';

import { useSyncExternalStore } from 'react';
// 桶（@/shared/api）経由だと client.ts が @/shared/lib を読み、初期化の循環になる
import { getSessionUserId } from '@/shared/api/session';
import { ClientDataError } from '../apiError';
import { hasMemoryOperations, type OperationStore, type SavedOperation } from '../operation-store';
import { getCurrentStoreId } from '../session';
import { createUuid } from '../uuid';

export interface PendingOperation<P> extends SavedOperation<P> {
  phase: 'submitting' | 'unknown';
}
interface Snapshot {
  pending: PendingOperation<unknown> | null;
  unreadable: boolean;
}
export type SendOperation<P, R> = (
  payload: P,
  key: string,
  config: { headers: { 'X-Store-ID': string } }
) => Promise<R>;

const NONE: Snapshot = { pending: null, unreadable: false };
const inFlight = new Set<string>();
const snapshots = new Map<string, Snapshot>();
const revisions = new Map<string, number>();
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};
const preventUnload = (event: BeforeUnloadEvent) => {
  event.preventDefault();
  event.returnValue = '';
};

/**
 * 本人（このタブのトークンの利用者 ID）と、画面が表示している店舗。電子メールは再利用されうるので本人は ID で
 * 表す。店舗は全タブ共有の cookie ではなく画面の店舗で決める（別タブの切替で作用域が動かないように）。
 */
function slotOf(name: string, storeId: string | null | undefined) {
  if (typeof window === 'undefined') return null;
  const user = getSessionUserId();
  return user !== undefined && storeId ? JSON.stringify([name, user, storeId]) : null;
}
function read(slot: string, store: OperationStore): Snapshot {
  let snapshot = snapshots.get(slot);
  if (!snapshot) {
    try {
      const saved = store.read(slot);
      snapshot = {
        pending: saved && { ...saved, phase: inFlight.has(slot) ? 'submitting' : 'unknown' },
        unreadable: false,
      };
    } catch {
      snapshot = { pending: null, unreadable: true };
    }
    snapshots.set(slot, snapshot);
  }
  return snapshot;
}
function changed(slot: string) {
  snapshots.delete(slot);
  revisions.set(slot, (revisions.get(slot) ?? 0) + 1);
  window.removeEventListener('beforeunload', preventUnload);
  if (inFlight.size || hasMemoryOperations())
    window.addEventListener('beforeunload', preventUnload);
  listeners.forEach(listener => listener());
}

// 業務拒否は受領の照会の後に起きるので、初回でも再送でも初回が成立していないことを確定する。
// 認可拒否は照会の前に起きるので、初回が成立したかについて何も語らない（初回送信でだけ忘れてよい）。
const rejected = [400, 404, 409, 422];
const unauthorized = [401, 403];

async function execute<P, R>(
  name: string,
  storeId: string | null | undefined,
  slot: string | null,
  store: OperationStore,
  send: SendOperation<P, R>,
  created?: { payload: P }
): Promise<{ value: R } | null> {
  if (!slot || inFlight.has(slot)) return null;
  const existing = store.read(slot) as SavedOperation<P> | null;
  const replay = !created;
  if (replay ? !existing : existing) return null;
  // 現在店舗の cookie は別タブの切替で変わる。画面の店舗と食い違うか本人が変わったら、送らずに止める。
  if (slotOf(name, storeId) !== slot || getCurrentStoreId() !== storeId)
    throw new ClientDataError(
      'ログイン中のアカウントか店舗が変わりました。画面を開き直してください。'
    );
  // 再送は保持した内容とキーをそのまま送る。内容を変えた送信は再送ではない。
  const saved = existing ?? { key: createUuid(), payload: created!.payload };
  // 保存できなければ送らない。応答を失っても同じキー・同じ内容で回復できる状態でだけ送る。
  store.write(slot, saved);
  inFlight.add(slot);
  changed(slot);
  try {
    const value = await send(saved.payload, saved.key, { headers: { 'X-Store-ID': storeId! } });
    store.remove(slot);
    return { value };
  } catch (error) {
    const status = (error as { response?: { status?: number } } | null)?.response?.status;
    if (
      status !== undefined &&
      (rejected.includes(status) || (!replay && unauthorized.includes(status)))
    )
      store.remove(slot);
    throw error;
  } finally {
    inFlight.delete(slot);
    changed(slot);
  }
}

/**
 * 応答を失いうる書き込みを一つの操作として扱う。キーは操作を起こしたときに一度だけ採番し、結果不明の操作は
 * 同じ本人・同じ店舗の作用域で、保持した内容のまま再送するまで忘れない（GLOSSARY「操作の受領」）。
 * start / replay は送らなかったとき null、成功したとき { value } を返し、失敗は例外のまま投げる。
 */
export function useOperation<P, R>(
  name: string,
  {
    storeId,
    store,
    send,
  }: { storeId: string | null | undefined; store: OperationStore; send: SendOperation<P, R> }
) {
  const slot = slotOf(name, storeId);
  const snapshot = useSyncExternalStore(
    subscribe,
    () => (slot ? read(slot, store) : NONE),
    () => NONE
  );
  return {
    /** 本人と店舗が確定している。確定するまで操作は起こせない。 */
    ready: slot !== null,
    pending: snapshot.pending as PendingOperation<P> | null,
    unreadable: snapshot.unreadable,
    start: (payload: P) => execute(name, storeId, slot, store, send, { payload }),
    replay: () => execute(name, storeId, slot, store, send),
  };
}

/** 操作の状態が変わるたびに増える。操作を起こさない画面が、操作の後に読み直すために使う。 */
export function useOperationRevision(name: string, storeId: string | null | undefined) {
  const slot = slotOf(name, storeId);
  return useSyncExternalStore(
    subscribe,
    () => (slot ? (revisions.get(slot) ?? 0) : 0),
    () => 0
  );
}
