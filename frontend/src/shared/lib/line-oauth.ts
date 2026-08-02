/** LINE の認可エンドポイント（認可コードフロー + PKCE）。 */
export const LINE_AUTHORIZE_ENDPOINT = 'https://access.line.me/oauth2/v2.1/authorize';

/** LINE から要求する権限。表示名の取得に profile、id_token に openid が必要。 */
const LINE_SCOPE = 'profile openid';

/** state / code_verifier / 開始時の意図の保管先。単一のコールバック画面が三つの入口を捌くため意図も持たせる。 */
const STORAGE_KEY = 'kizuna-line-oauth';

/** LINE 認可を開始した入口。ログイン（未登録なら会員登録へ分岐）と既存アカウントへの連携。 */
export type LineOauthIntent = 'login' | 'link';

interface LineAuthorizationRecord {
  state: string;
  verifier: string;
  intent: LineOauthIntent;
}

function toBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** 32 バイト乱数の base64url 表現（43 文字）。PKCE の code_verifier が要求する 43〜128 文字を満たす。 */
function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return toBase64Url(bytes);
}

/** LINE 側に登録するコールバック URL。認可要求と引き換え要求で同一値でなければならない。 */
export function lineCallbackRedirectUri(): string {
  return `${window.location.origin}/platform/line/callback`;
}

/**
 * state と code_verifier を生成して sessionStorage に保存し、認可 URL を組み立てる。
 * SubtleCrypto は安全なコンテキスト（HTTPS ないし localhost）でしか公開されないため、
 * 平文 HTTP で開いた場合はここで失敗する（呼び出し元が利用者向けの案内を出す）。
 */
export async function prepareLineAuthorization(
  channelId: string,
  intent: LineOauthIntent
): Promise<string> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('SubtleCrypto is unavailable');
  }

  const state = randomToken();
  const verifier = randomToken();
  const digest = await subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  const challenge = toBase64Url(new Uint8Array(digest));

  const record: LineAuthorizationRecord = { state, verifier, intent };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(record));

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: channelId,
    redirect_uri: lineCallbackRedirectUri(),
    state,
    scope: LINE_SCOPE,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  return `${LINE_AUTHORIZE_ENDPOINT}?${params.toString()}`;
}

/** 認可 URL を組み立てて LINE へ遷移する。 */
export async function startLineAuthorization(
  channelId: string,
  intent: LineOauthIntent
): Promise<void> {
  const url = await prepareLineAuthorization(channelId, intent);
  window.location.assign(url);
}

/**
 * コールバックで受け取った state を保存値と照合し、code_verifier と意図を取り出す。
 * 保存値は照合の成否に依らず破棄する（認可要求 1 回に対して引き換えは 1 回）。
 * 保存が無い・state が食い違う場合は null（改竄ないし別タブでの開始）。
 */
export function consumeLineAuthorization(state: string | null): LineAuthorizationRecord | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  sessionStorage.removeItem(STORAGE_KEY);
  if (!raw || !state) return null;

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object') return null;

  const record = parsed as Partial<LineAuthorizationRecord>;
  if (typeof record.state !== 'string' || typeof record.verifier !== 'string') return null;
  if (record.intent !== 'login' && record.intent !== 'link') return null;
  if (record.state !== state) return null;

  return { state: record.state, verifier: record.verifier, intent: record.intent };
}
