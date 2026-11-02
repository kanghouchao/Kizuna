import Cookies from 'js-cookie';
import { endSession, getSessionUserId, storeSessionToken, type EndReason } from '../session';
import { rememberMemberReturnPath, takeMemberReturnPath } from '@/shared/lib/session';

const broadcasts: unknown[] = [];
class FakeBroadcastChannel {
  postMessage(message: unknown) {
    broadcasts.push(message);
  }
  close() {}
}

type ReturnPath = 'keep' | 'remember' | 'discard';
type Row = [
  EndReason,
  { cookies: boolean; operations: boolean; broadcast: boolean; returnPath: ReturnPath },
];

// 各行は「何を消し、何を残すか」をすべて固定する。残す側も見ないと、消す範囲を広げる変更が緑のまま通る。
const table: Row[] = [
  ['logout', { cookies: true, operations: true, broadcast: true, returnPath: 'keep' }],
  ['unsupported', { cookies: true, operations: true, broadcast: false, returnPath: 'discard' }],
  ['expired', { cookies: false, operations: false, broadcast: false, returnPath: 'remember' }],
  ['other-tab', { cookies: false, operations: false, broadcast: false, returnPath: 'keep' }],
  [
    'credential-changed',
    { cookies: false, operations: false, broadcast: false, returnPath: 'keep' },
  ],
  ['relogin', { cookies: true, operations: true, broadcast: false, returnPath: 'remember' }],
  ['resume-failed', { cookies: false, operations: false, broadcast: false, returnPath: 'keep' }],
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
    Cookies.set('current-store-id', '1');
    sessionStorage.setItem('pending-operation:slot', '{}');
    // 預けてあった戻り先と、終了時の現在地を別の値にして、残す・預け直す・捨てるを見分ける
    window.history.pushState({}, '', '/member/old');
    rememberMemberReturnPath('/member/old', '#old');
    window.history.pushState({}, '', '/member/receipts?s=1#tok3n');
  });

  afterEach(() => {
    Cookies.remove('session-kind');
    Cookies.remove('current-store-id');
    sessionStorage.clear();
    takeMemberReturnPath();
    window.history.pushState({}, '', '/');
  });

  it.each(table)('%s', (reason, expected) => {
    endSession(reason);

    expect(getSessionUserId()).toBeUndefined();
    expect(Cookies.get('session-kind')).toBe(expected.cookies ? undefined : 'member');
    expect(Cookies.get('current-store-id')).toBe(expected.cookies ? undefined : '1');
    expect(sessionStorage.getItem('pending-operation:slot')).toBe(
      expected.operations ? null : '{}'
    );
    expect(broadcasts).toEqual(expected.broadcast ? [{ user_id: 7 }] : []);
    expect(takeMemberReturnPath()).toBe(
      {
        keep: '/member/old#old',
        remember: '/member/receipts?s=1#tok3n',
        discard: null,
      }[expected.returnPath]
    );
  });
});
