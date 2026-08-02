import Cookies from 'js-cookie';

/**
 * token cookie（JWT）の payload から読む claim。名前と形状はバックエンドの
 * PlatformAuthService.login が鋳造する claim（authorities / userType / storeBridge）との
 * wire 契約で、勝手に変えられない。
 *
 * JWT は「署名付き・非暗号化」— payload は base64url にすぎず、保持者が自分の claim を
 * 読むのは設計どおり。改竄は署名不一致でサーバが 401 にするため、ここで読んだ値は
 * 表示・導線制御にのみ使う（権限の強制は各エンドポイントのサーバ側認可）。
 */
export interface TokenClaims {
  /** STAFF は PERM_ 接頭辞の権限並集、CAST / MEMBER は ROLE_CAST / ROLE_MEMBER の標識。 */
  authorities: string[];
  /** STAFF / CAST / MEMBER。 */
  userType: string | undefined;
  /** 店舗文脈（X-Store-ID）を確立できるか。 */
  storeBridge: boolean;
}

/** base64url（padding 無し）をデコードして UTF-8 文字列を返す。 */
function decodeBase64Url(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
  const binary = window.atob(padded);
  // atob の返す binary string から percent-encoding 経由で UTF-8 を復元する
  //（TextDecoder は jest の jsdom 環境に存在しない）
  return decodeURIComponent(
    Array.from(binary, char => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
  );
}

/**
 * 現在の token cookie の claim を読む。cookie が無い・JWT の形をしていない・payload が
 * 壊れている場合は null（＝資格なし扱い。最終防衛線はサーバの署名検証と認可）。
 * 保存や失効管理は持たない — 値は常に現在の cookie から導出され、token の差し替えと
 * 原子的に切り替わる。
 */
export function readTokenClaims(): TokenClaims | null {
  const token = Cookies.get('token');
  if (!token) return null;
  const segments = token.split('.');
  if (segments.length !== 3) return null;
  try {
    const payload = JSON.parse(decodeBase64Url(segments[1])) as Record<string, unknown>;
    return {
      authorities: Array.isArray(payload.authorities)
        ? payload.authorities.filter((entry): entry is string => typeof entry === 'string')
        : [],
      userType: typeof payload.userType === 'string' ? payload.userType : undefined,
      storeBridge: payload.storeBridge === true,
    };
  } catch {
    return null;
  }
}

/** 権限コード（例: CAST_INVITE）を保持しているか。claim 上の表現は PERM_ 接頭辞付き。 */
export function hasPermission(claims: TokenClaims | null, permission: string): boolean {
  return claims?.authorities.includes(`PERM_${permission}`) ?? false;
}
