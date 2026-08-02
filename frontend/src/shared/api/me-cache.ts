// GET /platform/me の応答キャッシュの保管庫。応答の型は利用側（entities/user）が知り、ここは
// 「token 指紋で照合できる 1 枠の記録」という保存の関心だけを持つ。shared 層に置くのは、
// apiClient の強制退場（401・旧形式 cookie の 403）でも破棄できるようにするため
//（FSD の依存は下向きのみで、shared から entities は呼べない）。

const ME_CACHE_KEY = 'platform-me-cache';

// 鍵には token そのものではなく一方向の指紋を保存する。token を保存すると、cookie の除去だけで
// 終わるセッション破棄経路の後も有効な JWT が localStorage から回収できてしまう。照合できれば
// 十分で、復元できてはならない。crypto.subtle は insecure context（http の開発環境）で使えない
// ため、同期の非暗号ハッシュ 2 本の連結で衝突面だけ確保する（衝突しても別 token のキャッシュを
// 表示に使うだけで、権限の強制はサーバ側にある）。
function tokenFingerprint(token: string): string {
  let h1 = 5381 | 0;
  let h2 = 52711 | 0;
  for (let i = 0; i < token.length; i++) {
    const code = token.charCodeAt(i);
    h1 = (Math.imul(h1, 33) + code) | 0;
    h2 = (Math.imul(h2, 31) + code) | 0;
  }
  return `${h1.toString(36)}.${h2.toString(36)}`;
}

interface MeCacheRecord {
  fingerprint?: string;
  /** 応答本体。無ければ「失効印だけの記録」（変異後の tombstone）。 */
  me?: unknown;
  /** 書き込み時刻（epoch millis）。遅延した GET 応答が新しい書き込みを潰さないための順序印。 */
  writtenAt?: number;
}

// storage へ書けなかった変異印の控え。同一タブ内では persistence が失敗しても
// 「発送より新しい変異があった」ことを覚えておき、遅延 GET の書き込みを弾く。
let lastMutationAt = 0;

function readCacheRecord(): MeCacheRecord | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(ME_CACHE_KEY);
    return raw ? (JSON.parse(raw) as MeCacheRecord) : null;
  } catch {
    // 壊れた保存値は無いものとして扱う（次の取得成功時に上書きされる）
    return null;
  }
}

export function readCachedMe(token: string): unknown | null {
  const record = readCacheRecord();
  return record?.fingerprint === tokenFingerprint(token) && record.me !== undefined
    ? record.me
    : null;
}

/** since 以降に同一 token 向けの書き込み・変異が済んでいるか（遅延応答の上書き判定に使う）。 */
export function hasNewerWrite(token: string, since: number): boolean {
  if (lastMutationAt >= since) return true;
  const record = readCacheRecord();
  return record?.fingerprint === tokenFingerprint(token) && (record.writtenAt ?? 0) >= since;
}

// 保存だけを消す（in-memory の変異控えは残す）。書き込み失敗の後始末用で、
// セッション破棄は clearMeCache が担う。
function removeStoredRecord(): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(ME_CACHE_KEY);
  } catch {
    // storage が塞がれていても呼び出し元の処理を止めない
  }
}

export function writeCachedMe(token: string, me: unknown): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(
      ME_CACHE_KEY,
      JSON.stringify({
        fingerprint: tokenFingerprint(token),
        me,
        writtenAt: Date.now(),
      } satisfies MeCacheRecord)
    );
  } catch {
    // 書けない環境で古い値が残り続けると、成功した更新の後も次の me() が旧値を
    // 返し続ける。既存の記録も best-effort で消し、次の読みはサーバへ倒す
    removeStoredRecord();
  }
}

/**
 * 変異（PUT /platform/me）後に呼ぶ。応答の全量を書き戻すと、同一 token の並行変異
 * （別タブの updateMe・遅延中の GET）との順序をクライアントでは正しく決められないため、
 * 「以後の読みはサーバへ」という失効印だけを残す。
 */
export function markMeCacheStale(token: string): void {
  // 保存に失敗しても同一タブ内の遅延 GET は弾けるよう、in-memory の控えは先に必ず立てる
  lastMutationAt = Date.now();
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(
      ME_CACHE_KEY,
      JSON.stringify({
        fingerprint: tokenFingerprint(token),
        writtenAt: Date.now(),
      } satisfies MeCacheRecord)
    );
  } catch {
    removeStoredRecord();
  }
}

/** セッション破棄（logout・401/403 の強制退場）で呼ぶ。共有端末に個人情報を残さない。 */
export function clearMeCache(): void {
  // セッションが終わるので、書けなかった変異の控えも次のセッションへ持ち越さない
  lastMutationAt = 0;
  removeStoredRecord();
}
