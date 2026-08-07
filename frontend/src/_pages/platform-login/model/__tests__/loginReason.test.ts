import { resolveLoginReason } from '../loginReason';

/**
 * 白名単は 2 件。落点は 3 つあるが、そのうち店舗コンソール入口の行き止まりは
 * 原地で名乗る形（遷移しない）なので理由コードを持たない。
 */
describe('ログイン画面の理由コード', () => {
  it('パスワード変更後は、変更が済んだことと次に何をするかを名乗る', () => {
    expect(resolveLoginReason('password-changed')).toBe(
      'パスワードを変更しました。新しいパスワードでログインしてください'
    );
  });

  it('会話が使えなくなった場合は、期限に言及せずに名乗る', () => {
    // 旧形式 cookie の 403 はトークン自体が期限内であり、「有効期限が切れました」は嘘になる。
    const copy = resolveLoginReason('expired');

    expect(copy).toBe(
      'ログインの状態が無効になりました。お手数ですが、もう一度ログインしてください'
    );
    expect(copy).not.toContain('有効期限');
  });

  it('未指定なら何も出さない', () => {
    expect(resolveLoginReason(undefined)).toBeUndefined();
  });

  it.each([
    'unknown',
    '',
    'password-changed ',
    'PASSWORD-CHANGED',
    // 素のオブジェクトを表に使うと、言語側の名前が文字列でない値を返して描画が壊れる
    'constructor',
    '__proto__',
    'toString',
    // 白名単が無ければログイン画面に代弁させられる文言
    'ただいまメンテナンス中です。サポートへ連絡先をお伝えください',
    '<script>alert(1)</script>',
  ])('白名単に無い値（%s）では何も出さない', reason => {
    expect(resolveLoginReason(reason)).toBeUndefined();
  });

  it('同じ名前を複数回渡されても（配列で届く）何も出さない', () => {
    expect(resolveLoginReason(['password-changed', 'expired'])).toBeUndefined();
  });
});
