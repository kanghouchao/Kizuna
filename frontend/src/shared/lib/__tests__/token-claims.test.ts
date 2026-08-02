import Cookies from 'js-cookie';
import { hasPermission, readTokenClaims } from '../token-claims';

/** base64url（padding 無し）— JWT の segment 表現。 */
const encodeSegment = (value: object): string =>
  Buffer.from(JSON.stringify(value), 'utf-8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');

const jwt = (payload: object): string =>
  `${encodeSegment({ alg: 'HS256' })}.${encodeSegment(payload)}.signature`;

describe('readTokenClaims', () => {
  afterEach(() => {
    Cookies.remove('token');
  });

  it('token cookie の payload から authorities / userType / storeBridge を読む', () => {
    Cookies.set(
      'token',
      jwt({
        authorities: ['PERM_CAST_INVITE', 'PERM_ORDER_MANAGE'],
        userType: 'STAFF',
        storeBridge: true,
        sub: 'staff@kizuna.test',
      })
    );

    expect(readTokenClaims()).toEqual({
      authorities: ['PERM_CAST_INVITE', 'PERM_ORDER_MANAGE'],
      userType: 'STAFF',
      storeBridge: true,
    });
  });

  // base64url は padding を落とすため、payload 長により復元時の補完量が 0〜2 文字で変わる。
  // どの剰余でも壊れないことを、長さの異なる payload で固定する
  it('payload 長（base64 padding の剰余）によらず復元できる', () => {
    for (const filler of ['a', 'ab', 'abc', 'abcd']) {
      Cookies.set('token', jwt({ userType: 'CAST', filler }));
      expect(readTokenClaims()?.userType).toBe('CAST');
    }
  });

  it('payload に非 ASCII（UTF-8）が含まれていても壊れない', () => {
    Cookies.set('token', jwt({ userType: 'MEMBER', note: '日本語の値' }));

    expect(readTokenClaims()?.userType).toBe('MEMBER');
  });

  it('token cookie が無ければ null', () => {
    expect(readTokenClaims()).toBeNull();
  });

  it('JWT の形（3 セグメント）でない token は null', () => {
    Cookies.set('token', 'opaque-session-token');

    expect(readTokenClaims()).toBeNull();
  });

  it('payload が base64/JSON として壊れている token は null', () => {
    Cookies.set('token', 'aGVhZGVy.%%%broken%%%.signature');

    expect(readTokenClaims()).toBeNull();
  });

  it('claim の欠落・型違いは安全側の既定値に倒す', () => {
    Cookies.set('token', jwt({ authorities: 'PERM_X', storeBridge: 'yes' }));

    expect(readTokenClaims()).toEqual({
      authorities: [],
      userType: undefined,
      storeBridge: false,
    });
  });

  it('authorities の文字列でない要素は除外する', () => {
    Cookies.set('token', jwt({ authorities: ['PERM_A', 1, null, 'PERM_B'] }));

    expect(readTokenClaims()?.authorities).toEqual(['PERM_A', 'PERM_B']);
  });
});

describe('hasPermission', () => {
  it('権限コードを PERM_ 接頭辞付きの claim 表現で照合する', () => {
    const claims = { authorities: ['PERM_CAST_INVITE'], userType: 'STAFF', storeBridge: true };

    expect(hasPermission(claims, 'CAST_INVITE')).toBe(true);
    expect(hasPermission(claims, 'CAST_FIELD_DEF_MANAGE')).toBe(false);
  });

  it('claims が null（未認証・壊れた token）なら常に false', () => {
    expect(hasPermission(null, 'CAST_INVITE')).toBe(false);
  });
});
