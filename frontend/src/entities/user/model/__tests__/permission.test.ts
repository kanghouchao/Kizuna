import { hasPermission } from '../permission';
import type { MeResponse } from '../types';

const me = (permissions?: MeResponse['permissions']): MeResponse => ({
  email: 'staff@example.com',
  user_type: 'STAFF',
  permissions,
  line_linked: false,
});

describe('hasPermission', () => {
  it('取得前・取得失敗（me が null）は持たない扱い', () => {
    expect(hasPermission(null, 'ORDER_MANAGE')).toBe(false);
  });

  it('permissions が無い応答は持たない扱い', () => {
    expect(hasPermission(me(undefined), 'ORDER_MANAGE')).toBe(false);
  });

  it('/me の permissions にある権限だけを持つ', () => {
    const value = me(['ORDER_MANAGE']);
    expect(hasPermission(value, 'ORDER_MANAGE')).toBe(true);
    expect(hasPermission(value, 'ORDER_CORRECT')).toBe(false);
  });
});
