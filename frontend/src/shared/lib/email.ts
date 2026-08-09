/**
 * noValidate で type="email" の執行が止まるぶんを引き継ぐ検証規則の値。
 * ドメインに . を求める点で原生より厳しいが、これは既に StoreEditPage が採っている形。
 */
export const EMAIL_PATTERN = /^([^\s@])+@([^\s@])+\.[^\s@]+$/;

/** 形式違反のときに欄の傍へ出す文言。全フォームで同一。 */
export const EMAIL_PATTERN_MESSAGE = 'メールアドレスの形式が正しくありません';
