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

export function hasMemoryOperations(): boolean {
  return memory.size > 0;
}

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

/**
 * セッションの終了で、再読込を越えて保持した操作を忘れる。メモリの操作は本人 ID で区切られ、タブを閉じれば
 * 消えるので対象にしない。保存領域が利用不能でも終了は完了させる。
 */
export function forgetAll(): void {
  try {
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const key = sessionStorage.key(i);
      if (key?.startsWith(prefix)) sessionStorage.removeItem(key);
    }
  } catch {
    // 呼出側（ログアウト等）を止めない。
  }
}
