// GET /platform/me の応答キャッシュの保管庫。応答の型は利用側（entities/user）が知り、ここは
// 「token 指紋で照合できる 1 枠の記録」という保存の関心だけを持つ。shared 層に置くのは、
// apiClient の強制退場（401・旧形式 cookie の 403）でも破棄できるようにするため
//（FSD の依存は下向きのみで、shared から entities は呼べない）。
//
// 記録は 3 種の key に分かれる:
// - ME_CACHE_KEY: 応答本体（GET の継続だけが書く）
// - ME_STALE_PREFIX + 指紋: 失効標（変異 updateMe だけが書く。指紋ごとに独立の key —
//   1 つの写像に束ねると read-modify-write になり、別タブの変異と交錯して片方の標が失われる）
// - ME_SEQ_KEY:  単調増加の序数（新旧の裁定に使う。壁時計は時刻補正・VM 復帰で逆行し得るため
//                順序付けに使わない）
// localStorage には検査と書き込みを跨ぐ原子性が無く、「新しい書き込みが無いか確認してから書く」
// 方式は別タブの変異と交錯し得る。key を分けると GET の書き込みは失効標に触れられず、
// 鮮度の裁定は読み取り側（失効標の序数 vs 応答の as-of 序数の比較）で常に成立する。
const ME_CACHE_KEY = 'platform-me-cache';
const ME_STALE_PREFIX = 'platform-me-stale.';
const ME_SEQ_KEY = 'platform-me-seq';

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
  me?: unknown;
  /** GET 発送時点の序数。これより大きい序数の失効標があれば陳腐。 */
  asOfSeq?: number;
}

/** 失効標の保持上限。トークンは寿命が短く、古い標から捨てても実害は無い。 */
const MAX_STALE_ENTRIES = 8;

// storage へ書けなかった変異の控え。同一タブ内では persistence が失敗しても
// 「この序数より古い応答は陳腐」という裁定を残す。
let lastMutationSeq = 0;

function readJson<T>(key: string): T | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    // 壊れた保存値は無いものとして扱う（次の取得成功時に上書きされる）
    return null;
  }
}

function removeKey(key: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(key);
  } catch {
    // storage が塞がれていても呼び出し元の処理を止めない
  }
}

/** 現在の序数。GET は発送時にこれを読み、記録の as-of とする。 */
export function currentMeSeq(): number {
  const raw = readJson<unknown>(ME_SEQ_KEY);
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : 0;
}

/** 序数を 1 進めて返す。変異だけが呼ぶ。書けない環境でも呼び出し元の in-memory 控えには使える。 */
function bumpMeSeq(): number {
  const next = currentMeSeq() + 1;
  if (typeof window === 'undefined') return next;
  try {
    window.localStorage.setItem(ME_SEQ_KEY, JSON.stringify(next));
  } catch {
    // 序数が書けなくても、失効標側の失敗処理（本体の破棄）が陳腐配信を防ぐ
  }
  return next;
}

/** 指紋の失効標（変異序数）。無ければ 0。 */
function readStaleSeq(fingerprint: string): number {
  const raw = readJson<unknown>(ME_STALE_PREFIX + fingerprint);
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : 0;
}

/** 失効標の key を全て列挙する（掃除・破棄用）。 */
function listStaleKeys(): string[] {
  if (typeof window === 'undefined') return [];
  try {
    const keys: string[] = [];
    for (let i = 0; i < window.localStorage.length; i++) {
      const key = window.localStorage.key(i);
      if (key?.startsWith(ME_STALE_PREFIX)) keys.push(key);
    }
    return keys;
  } catch {
    return [];
  }
}

/** token に対応する新鮮な応答を返す。as-of より大きい序数の失効標がある記録は陳腐として扱わない。 */
export function readCachedMe(token: string): unknown | null {
  const record = readJson<MeCacheRecord>(ME_CACHE_KEY);
  if (record?.fingerprint !== tokenFingerprint(token) || record.me === undefined) return null;
  const asOfSeq = record.asOfSeq ?? 0;
  if (lastMutationSeq > asOfSeq) return null;
  if (readStaleSeq(record.fingerprint) > asOfSeq) return null;
  return record.me;
}

/**
 * GET の継続から呼ぶ。asOfSeq は GET 発送時点の序数（currentMeSeq）。失効標はここでは
 * 触れないため、変異と交錯して陳腐な応答を書いてしまっても、読み取り側の裁定で必ず弾かれる。
 */
export function writeCachedMe(token: string, me: unknown, asOfSeq: number): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(
      ME_CACHE_KEY,
      JSON.stringify({
        fingerprint: tokenFingerprint(token),
        me,
        asOfSeq,
      } satisfies MeCacheRecord)
    );
  } catch {
    // 書けない環境で古い値が残り続けると、成功した更新の後も次の me() が旧値を
    // 返し続ける。既存の記録も best-effort で消し、次の読みはサーバへ倒す
    removeKey(ME_CACHE_KEY);
  }
}

/**
 * 変異（PUT /platform/me）の発送前と応答後に呼ぶ。応答の全量を書き戻すと、同一 token の
 * 並行変異（別タブの updateMe・遅延中の GET）との順序をクライアントでは正しく決められない
 * ため、「この序数より古い応答は陳腐」という失効標だけを残す。
 */
export function markMeCacheStale(token: string): void {
  const seq = bumpMeSeq();
  // 保存に失敗しても同一タブ内の遅延 GET は弾けるよう、in-memory の控えは必ず立てる
  lastMutationSeq = seq;
  if (typeof window === 'undefined') return;
  try {
    // 指紋ごとに独立の key へ 1 回の setItem で書く（read-modify-write を作らない）
    window.localStorage.setItem(ME_STALE_PREFIX + tokenFingerprint(token), JSON.stringify(seq));
    // 上限を超えたら古い標（小さい序数）から捨てる。掃除は古い標にしか触れないため、
    // 直前に書かれた標（最大級の序数）を消すことはない
    const entries = listStaleKeys()
      .map(key => [key, readStaleSeq(key.slice(ME_STALE_PREFIX.length))] as const)
      .sort(([, a], [, b]) => b - a);
    for (const [key] of entries.slice(MAX_STALE_ENTRIES)) {
      removeKey(key);
    }
  } catch {
    // 失効標すら書けないなら、陳腐な応答が残らないよう本体を消して読みをサーバへ倒す
    removeKey(ME_CACHE_KEY);
  }
}

/** セッション破棄（logout・401/403 の強制退場・ログイン画面の掃除）で呼ぶ。共有端末に個人情報を残さない。 */
export function clearMeCache(): void {
  // セッションが終わるので、書けなかった変異の控えも次のセッションへ持ち越さない。
  // 序数（ME_SEQ_KEY）は破棄しない — 消すと他タブの in-flight な記録との大小関係が壊れる
  lastMutationSeq = 0;
  removeKey(ME_CACHE_KEY);
  for (const key of listStaleKeys()) {
    removeKey(key);
  }
}
