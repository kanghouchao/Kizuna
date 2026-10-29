/**
 * ホスト名の形（ラベルを . で連ねた形）。`/` `:` `@` を含む値は通さない。
 * 各ラベルは DNS の上限に合わせ 63 文字以下に制限する（64 文字以上は名前解決できず到達不能になる）。
 */
const HOSTNAME = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$/;

/**
 * 店舗ドメインがホスト名の形をしているか。
 *
 * リンクとして描画する前に必ず通すこと。`正規.example@攻撃者.example` のような値は
 * プロトコル相対 URL の中で前半が userinfo と解釈され、別ホストへの導線になる。
 * 保存の手前でも同じ形を検査しているが、それ以前に入った行は検査を経ていない。
 */
export function isStoreDomain(domain: string): boolean {
  return HOSTNAME.test(domain);
}

/** 略称の字数上限。コードポイントで数えるため、全角・半角とも 1 文字。 */
const SHORT_NAME_MAX = 3;

/** 略称の検証。前後の空白を除いた値が 1〜3 文字なら null、そうでなければ利用者向けの文言を返す。 */
export function storeShortNameError(shortName: string): string | null {
  const length = [...shortName.trim()].length;
  if (length === 0) return '略称は必須です';
  if (length > SHORT_NAME_MAX) return `略称は ${SHORT_NAME_MAX} 文字以内で入力してください`;
  return null;
}
