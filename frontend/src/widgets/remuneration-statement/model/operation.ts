import { useSyncExternalStore } from 'react';
import { remunerationApi } from '../api';
import type { Bonus, Guarantee } from './types';
import { authApi } from '@/entities/user';
import { ClientDataError, getCurrentStoreId } from '@/shared/lib';

export type EditTarget =
  | { kind: 'guarantee'; item?: Guarantee; version: number; cancel?: boolean }
  | { kind: 'bonus'; item?: Bonus; cancel?: boolean };
export interface EditValues {
  date: string;
  amount: number | undefined;
  stopped: boolean;
  reason: string;
  correction_reason: string;
}
export interface Operation {
  scope: string;
  personId: number;
  personName: string;
  target: EditTarget;
  values: EditValues;
  requestId: string;
}
interface PendingOperation {
  operation: Operation;
  phase: 'submitting' | 'unknown';
}
// 金額・理由はブラウザの永続領域に保存しない。画面内の対象切替や閉じ直しを越えて未確認要求を保持する。
const pending = new Map<string, PendingOperation>();
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
function update(scope: string, value: PendingOperation | null) {
  if (value) pending.set(scope, value);
  else pending.delete(scope);
  window.removeEventListener('beforeunload', preventUnload);
  if (pending.size) window.addEventListener('beforeunload', preventUnload);
  listeners.forEach(listener => listener());
}
/** 操作者（/me の email）と現在店舗の組。どちらかが未確定なら null。 */
export function operationScope(email: string | undefined): string | null {
  if (typeof window === 'undefined') return null;
  const store = getCurrentStoreId();
  return email && store ? JSON.stringify([email, store]) : null;
}
export function usePendingOperation(scope: string | null) {
  return useSyncExternalStore(
    subscribe,
    () => (scope ? (pending.get(scope) ?? null) : null),
    () => null
  );
}
export async function submitOperation(operation: Operation, replay = false): Promise<boolean> {
  const blocked = () => {
    const existing = pending.get(operation.scope);
    return replay ? existing?.phase !== 'unknown' || existing.operation !== operation : !!existing;
  };
  if (blocked()) return false;
  // 操作者は画面の /me（layout の生存期間）で、token は別タブのログインで差し替わる。送る前に今の
  // token の持ち主と現在店舗を確かめ、元の作用域でない操作は何も預けずに止める。
  if (operationScope((await authApi.me()).email) !== operation.scope)
    throw new ClientDataError(
      'ログイン中のアカウントか店舗が変わりました。画面を開き直してください。'
    );
  if (blocked()) return false;
  update(operation.scope, { operation, phase: 'submitting' });
  try {
    await send(operation);
  } catch (error) {
    const status = (error as { response?: { status?: number } })?.response?.status;
    // 一度応答を失った要求は、後続の認可失敗や競合でも未確定のまま。成功した同一要求の再送だけが解決する。
    const rejected =
      !replay && status !== undefined && [400, 401, 403, 404, 409, 422].includes(status);
    update(operation.scope, rejected ? null : { operation, phase: 'unknown' });
    throw error;
  }
  update(operation.scope, null);
  return true;
}
async function send({ personId, target, values, requestId }: Operation) {
  if (target.cancel && target.item) {
    const body = {
      reason: values.reason.trim(),
      request_id: requestId,
      expected_version: target.kind === 'guarantee' ? target.version : target.item.version,
    };
    if (target.kind === 'guarantee') await remunerationApi.cancelGuarantee(target.item.id, body);
    else await remunerationApi.cancelBonus(target.item.id, body);
  } else if (target.kind === 'guarantee') {
    const body = {
      effective_from: values.date,
      state: values.stopped ? ('STOPPED' as const) : ('ACTIVE' as const),
      daily_amount: values.stopped ? null : (values.amount ?? 0),
      reason: values.reason.trim(),
      request_id: requestId,
      expected_version: target.version,
    };
    if (target.item)
      await remunerationApi.correctGuarantee(target.item.id, {
        ...body,
        correction_reason: values.correction_reason.trim(),
      });
    else await remunerationApi.createGuarantee(personId, body);
  } else {
    const body = {
      award_date: values.date,
      amount: values.amount ?? 0,
      reason: values.reason.trim(),
      request_id: requestId,
    };
    if (target.item)
      await remunerationApi.correctBonus(target.item.id, {
        ...body,
        expected_version: target.item.version,
        correction_reason: values.correction_reason.trim(),
      });
    else await remunerationApi.createBonus(personId, body);
  }
}
export function createRequestId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
