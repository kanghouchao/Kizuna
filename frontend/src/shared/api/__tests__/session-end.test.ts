import Cookies from 'js-cookie';
import {
  endSession,
  getSessionUserId,
  landSession,
  storeSessionToken,
  type EndReason,
} from '../session';

const broadcasts: unknown[] = [];
class FakeBroadcastChannel {
  postMessage(message: unknown) {
    broadcasts.push(message);
  }
  close() {}
}

type Row = [EndReason, { cookies: boolean; operations: boolean; broadcast: boolean }];

// 各行は「何を消し、何を残すか」をすべて固定する。残す側も見ないと、消す範囲を広げる変更が緑のまま通る。
const table: Row[] = [
  ['logout', { cookies: true, operations: true, broadcast: true }],
  ['unsupported', { cookies: true, operations: true, broadcast: false }],
  ['expired', { cookies: false, operations: false, broadcast: false }],
  ['other-tab', { cookies: false, operations: false, broadcast: false }],
  ['credential-changed', { cookies: false, operations: false, broadcast: false }],
  ['relogin', { cookies: true, operations: true, broadcast: false }],
  ['resume-failed', { cookies: false, operations: false, broadcast: false }],
];

describe('endSession の終了理由ごとの消す範囲', () => {
  const originalChannel = globalThis.BroadcastChannel;
  beforeAll(() => {
    globalThis.BroadcastChannel = FakeBroadcastChannel as unknown as typeof BroadcastChannel;
  });
  afterAll(() => {
    globalThis.BroadcastChannel = originalChannel;
  });

  beforeEach(() => {
    broadcasts.length = 0;
    storeSessionToken({ token: 'tkn', expires_at: 1, user_id: 7, refresh_expires_at: 2 });
    Cookies.set('session-kind', 'member');
    sessionStorage.setItem('pending-operation:slot', '{}');
  });

  afterEach(() => {
    Cookies.remove('session-kind');
    sessionStorage.clear();
  });

  it.each(table)('%s', (reason, expected) => {
    endSession(reason);

    expect(getSessionUserId()).toBeUndefined();
    expect(Cookies.get('session-kind')).toBe(expected.cookies ? undefined : 'member');
    expect(sessionStorage.getItem('pending-operation:slot')).toBe(
      expected.operations ? null : '{}'
    );
    expect(broadcasts).toEqual(expected.broadcast ? [{ user_id: 7 }] : []);
  });
});

describe('landSession の着地', () => {
  const LATER = Date.now() + 60_000;

  afterEach(() => {
    Cookies.remove('session-kind');
  });

  it('本人種別の戻り先があればそこへ、断片ごと着地する', () => {
    expect(landSession('member', LATER, '/member/receipts#tok3n')).toBe('/member/receipts#tok3n');
    expect(landSession('admin', LATER, '/store/12/casts')).toBe('/store/12/casts');
    expect(landSession('cast', LATER, '/cast/schedule')).toBe('/cast/schedule');
  });

  it.each([
    ['admin', '/member/receipts', '/admin'],
    ['member', 'https://evil.test/', '/member/'],
    ['cast', '//evil.test/cast/', '/cast/'],
    ['member', undefined, '/member/'],
  ] as const)('%s に戻り先 %s を渡しても既定の着地 %s へ進む', (kind, next, landing) => {
    expect(landSession(kind, LATER, next)).toBe(landing);
    expect(Cookies.get('session-kind')).toBe(kind);
  });
});
