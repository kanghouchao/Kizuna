import { advertisingApi, type Operation } from '../api/advertising';
const prefix = 'pending-operation:advertising:';
export interface Pending {
  operation: Operation;
  phase: 'submitting' | 'unknown';
}
const active = new Set<string>();
export function operationKey(subject: string, store: string, month: string) {
  return prefix + JSON.stringify([subject, store, month]);
}
export function readPending(key: string): Pending | null {
  const saved = sessionStorage.getItem(key);
  if (!saved) return null;
  const parsed = JSON.parse(saved) as Operation;
  if (operationKey(parsed.subject, parsed.store, parsed.month) !== key)
    throw new Error('保存した操作の対象を確認できません。');
  return { operation: parsed, phase: active.has(key) ? 'submitting' : 'unknown' };
}
export async function submitOperation(
  operation: Operation,
  replay: boolean,
  onChange: (p: Pending | null) => void
) {
  const key = operationKey(operation.subject, operation.store, operation.month);
  if (active.has(key)) return false;
  const existing = readPending(key);
  if (existing && (!replay || JSON.stringify(existing.operation) !== JSON.stringify(operation)))
    return false;
  if (replay && !existing) return false;
  // 送信前にタブ内へ保存できなければ送らない。応答消失後も同一内容・同一キーで回復する。
  sessionStorage.setItem(key, JSON.stringify(operation));
  active.add(key);
  window.dispatchEvent(new Event('advertising-operation'));
  onChange({ operation, phase: 'submitting' });
  let completed = false;
  try {
    await advertisingApi.mutate(operation);
    completed = true;
    sessionStorage.removeItem(key);
    onChange(null);
    return true;
  } catch (error) {
    const status = (error as { response?: { status?: number } }).response?.status;
    // 成功受領を先に照会する API の業務拒否は未成功を確定する。認可拒否では確定できない。
    if (
      status !== undefined &&
      ([400, 404, 409, 422].includes(status) || (!replay && [401, 403].includes(status)))
    ) {
      sessionStorage.removeItem(key);
      onChange(null);
    } else onChange({ operation, phase: 'unknown' });
    throw error;
  } finally {
    active.delete(key);
    window.dispatchEvent(new CustomEvent('advertising-operation', { detail: { key, completed } }));
  }
}
export function requestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, v => v.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
