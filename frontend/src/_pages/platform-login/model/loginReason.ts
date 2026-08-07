/**
 * ログイン画面が「なぜここに居るか」を名乗るための、理由コード → 固定文言の引き当て表。
 *
 * 遷移の直前に出した通知は、着地までに画面ごと破棄されて誰にも読まれない。そこで送り出す側は
 * 理由コードだけを query に載せ、文言はこの表が持つ。
 *
 * 白名単に無い値は何も返さない。query の文字列をそのまま描くと、`?reason=<任意の話術>` の
 * リンクを作るだけで誰でもログイン画面に代弁させられるため。
 *
 * Map なのは、素のオブジェクトだと `constructor` や `toString` といった言語側の名前が
 * 文字列でない値を返してしまうため。理由コードは利用者が任意に書ける値である。
 */
const LOGIN_REASON_COPY = new Map<string, string>([
  ['password-changed', 'パスワードを変更しました。新しいパスワードでログインしてください'],
  // 401（失効・不正なトークン）と旧形式 cookie の 403 が共有する一つのコード。後者のトークンは
  // 期限内で、古いのは cookie の形式なので「有効期限が切れました」とは言えない。
  ['expired', 'ログインの状態が無効になりました。お手数ですが、もう一度ログインしてください'],
]);

/**
 * query の `reason` を固定文言へ引き当てる。知らない値・未指定・複数指定はいずれも何も出さない
 * （`?reason=a&reason=b` は文字列ではなく配列で届く）。
 */
export function resolveLoginReason(reason: string | string[] | undefined): string | undefined {
  if (typeof reason !== 'string') return undefined;
  return LOGIN_REASON_COPY.get(reason);
}
