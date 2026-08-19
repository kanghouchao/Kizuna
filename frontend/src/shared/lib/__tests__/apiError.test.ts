import {
  getApiErrorMessage,
  isConflict,
  isNotFound,
  ClientDataError,
  requireId,
} from '@/shared/lib';

describe('getApiErrorMessage', () => {
  it('error フィールドを優先して返す', () => {
    const err = { response: { data: { error: 'サーバー側メッセージ' } } };
    expect(getApiErrorMessage(err, '代替')).toBe('サーバー側メッセージ');
  });

  it('error がなければ message を返す', () => {
    const err = { response: { data: { message: '登録エラー' } } };
    expect(getApiErrorMessage(err, '代替')).toBe('登録エラー');
  });

  it('レスポンス形状でなければ fallback を返す', () => {
    expect(getApiErrorMessage(new Error('boom'), '代替')).toBe('代替');
    expect(getApiErrorMessage(null, '代替')).toBe('代替');
    expect(getApiErrorMessage({ response: { data: {} } }, '代替')).toBe('代替');
  });

  it('error / message が文字列でなければ無視して fallback を返す', () => {
    expect(getApiErrorMessage({ response: { data: { error: { code: 1 } } } }, '代替')).toBe('代替');
    expect(getApiErrorMessage({ response: { data: { message: 42 } } }, '代替')).toBe('代替');
  });
});

describe('isConflict / isNotFound', () => {
  it('該当する状態コードだけを真とする', () => {
    expect(isConflict({ response: { status: 409 } })).toBe(true);
    expect(isNotFound({ response: { status: 404 } })).toBe(true);
    expect(isConflict({ response: { status: 404 } })).toBe(false);
    expect(isNotFound({ response: { status: 409 } })).toBe(false);
  });

  it('応答を伴わない失敗（瞬断など）は「見つからない」ではない', () => {
    // 通信失敗を 404 と同じ扱いにすると、再試行できる失敗まで行き止まりの案内になる
    expect(isNotFound(new Error('network'))).toBe(false);
    expect(isNotFound(null)).toBe(false);
    expect(isNotFound({ response: {} })).toBe(false);
  });
});

describe('requireId', () => {
  it('識別子が無いときは要求を組ませずに失敗させる', () => {
    // `?? ''` で素通しすると、単数の操作が一覧の URI へ飛ぶ（DELETE /store/shifts/）。
    // 届いた先の 404/405 は「保存に失敗しました」と見分けが付かない
    expect(() => requireId(undefined, 'シフト')).toThrow(ClientDataError);
    expect(() => requireId('', 'シフト')).toThrow(ClientDataError);
    expect(requireId('s1', 'シフト')).toBe('s1');
  });

  it('投げた文言はそのまま画面へ出る', () => {
    // 後端の応答を探しに行っても何も無いので、代替文言に潰されると原因が画面側にあることも消える
    let thrown: unknown;
    try {
      requireId(undefined, '当日実績');
    } catch (error) {
      thrown = error;
    }
    expect(getApiErrorMessage(thrown, '保存に失敗しました')).toContain('当日実績の識別子');
  });
});
