/**
 * noValidate で type="email" の執行が止まるぶんを引き継ぐ検証規則の値。
 *
 * 形は HTML 仕様が type="email" に定める正規表現そのもので、原生が弾いていたもの（ラベルの空いた
 * ドメイン・下線・先頭のハイフン）をそのまま弾く。仕様との違いはドメインにドットを求める一点だけで、
 * これは社内アドレスのような単一ラベル宛先を受け付けないという既存の判断を引き継いだもの。
 */
export const EMAIL_PATTERN =
  /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;

/** 形式違反のときに欄の傍へ出す文言。全フォームで同一。 */
export const EMAIL_PATTERN_MESSAGE = 'メールアドレスの形式が正しくありません';

/**
 * 整数以外を弾く規則を作る。noValidate は type="number" の暗黙の step=1 まで無効化するため、
 * これが無いと 1.5 が Integer の項目へそのまま届く。
 *
 * 空欄は通す。「未入力かどうか」を決めるのは required の仕事で、valueAsNumber の空欄は NaN、
 * 素の register なら空文字と、欄ごとに姿が違う——どれも「まだ何も入っていない」であって
 * 「整数でない」ではない。
 */
export const integerRule = (label: string) => (value: unknown) => {
  if (value === null || value === undefined || value === '') return true;
  const numeric = typeof value === 'number' ? value : Number(value);
  if (Number.isNaN(numeric)) return true;
  return Number.isInteger(numeric) || `${label}は整数で入力してください`;
};
