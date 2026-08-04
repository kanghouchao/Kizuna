import { getApiErrorMessage, isConflict, isNotFound } from '@/shared/lib';

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
