'use client';

import { useSyncExternalStore } from 'react';
// 桶（@/shared/api）経由だと client.ts が @/shared/lib を読み、初期化の循環になる
import { getSessionUserId } from '@/shared/api/session';
import { ClientDataError } from '../apiError';
import { getCurrentStoreId } from '../session';
import { createUuid } from '../uuid';

export interface SavedOperation<P = unknown> {
  key: string;
  payload: P;
}
/** 結果不明の操作の置き場。slot は（操作名・本人・店舗）ごとに一つ。 */
export interface OperationStore {
  read(slot: string): SavedOperation | null;
  write(slot: string, saved: SavedOperation): void;
  remove(slot: string): void;
}

// 金額・本文をブラウザの永続領域に置かない。タブを閉じるまで、画面の切替や閉じ直しを越えて保持する。
const memory = new Map<string, SavedOperation>();
export const memoryOperationStore: OperationStore = {
  read: slot => memory.get(slot) ?? null,
  write: (slot, saved) => void memory.set(slot, saved),
  remove: slot => void memory.delete(slot),
};

const prefix = 'pending-operation:';
/** 再読込を越えて保持する。読めない・形が違う保存値は例外にし、画面は新しい操作を止める。 */
export const sessionOperationStore: OperationStore = {
  read(slot) {
    const raw = sessionStorage.getItem(prefix + slot);
    if (raw === null) return null;
    const saved = JSON.parse(raw) as Partial<SavedOperation>;
    if (typeof saved?.key !== 'string' || !('payload' in saved))
      throw new Error('保存した操作を確認できません。');
    return saved as SavedOperation;
  },
  write: (slot, saved) => sessionStorage.setItem(prefix + slot, JSON.stringify(saved)),
  remove: slot => sessionStorage.removeItem(prefix + slot),
};

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

/** 本人（このタブのトークンの利用者 ID）と現在店舗。電子メールは再利用されうるので本人は ID で表す。 */
function currentScope() {
  if (typeof window === 'undefined') return null;
  const user = getSessionUserId();
  const store = getCurrentStoreId();
  return user !== undefined && store ? { user, store } : null;
}
function currentSlot(name: string) {
  const scope = currentScope();
  return scope ? JSON.stringify([name, scope.user, scope.store]) : null;
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
  if (inFlight.size || memory.size) window.addEventListener('beforeunload', preventUnload);
  listeners.forEach(listener => listener());
}

// 業務拒否は受領の照会の後に起きるので、初回でも再送でも初回が成立していないことを確定する。
// 認可拒否は照会の前に起きるので、初回が成立したかについて何も語らない（初回送信でだけ忘れてよい）。
const rejected = [400, 404, 409, 422];
const unauthorized = [401, 403];

async function execute<P, R>(
  name: string,
  slot: string | null,
  store: OperationStore,
  send: SendOperation<P, R>,
  created?: { payload: P }
): Promise<{ value: R } | null> {
  if (!slot || inFlight.has(slot)) return null;
  const existing = store.read(slot) as SavedOperation<P> | null;
  const replay = !created;
  if (replay ? !existing : existing) return null;
  // 現在店舗は全タブ共有の cookie で、別タブの切替で変わる。起こした作用域でない操作は送らずに止める。
  if (currentSlot(name) !== slot)
    throw new ClientDataError(
      'ログイン中のアカウントか店舗が変わりました。画面を開き直してください。'
    );
  // 再送は保持した内容とキーをそのまま送る。内容を変えた送信は再送ではない。
  const saved = existing ?? { key: createUuid(), payload: created!.payload };
  // 保存できなければ送らない。応答を失っても同じキー・同じ内容で回復できる状態でだけ送る。
  store.write(slot, saved);
  inFlight.add(slot);
  changed(slot);
  const storeId = (JSON.parse(slot) as [string, number, string])[2];
  try {
    const value = await send(saved.payload, saved.key, { headers: { 'X-Store-ID': storeId } });
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
  { store, send }: { store: OperationStore; send: SendOperation<P, R> }
) {
  const slot = currentSlot(name);
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
    start: (payload: P) => execute(name, slot, store, send, { payload }),
    replay: () => execute(name, slot, store, send),
  };
}

/** 操作の状態が変わるたびに増える。操作を起こさない画面が、操作の後に読み直すために使う。 */
export function useOperationRevision(name: string) {
  const slot = currentSlot(name);
  return useSyncExternalStore(
    subscribe,
    () => (slot ? (revisions.get(slot) ?? 0) : 0),
    () => 0
  );
}
