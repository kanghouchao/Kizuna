import { getApiErrorMessage } from '@/shared/lib';

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
