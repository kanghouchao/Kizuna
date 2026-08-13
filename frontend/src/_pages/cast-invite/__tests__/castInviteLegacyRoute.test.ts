import { redirect } from 'next/navigation';
import CastInviteLegacyRoute from '@/app/(public)/platform/invite/[token]/page';

jest.mock('next/navigation', () => ({
  redirect: jest.fn(),
}));

const mockedRedirect = redirect as unknown as jest.Mock;

describe('配布済み招待リンクの受け口', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('パスのトークンをフラグメントへ移して送り直す', async () => {
    // 招待は 72 時間有効。受け口が無いと配布済みリンクが全て即 404 になる
    await CastInviteLegacyRoute({ params: Promise.resolve({ token: 'raw-invitation-token' }) });

    expect(mockedRedirect).toHaveBeenCalledWith('/platform/invite#raw-invitation-token');
  });

  it('送り先のリクエストターゲットにトークンを残さない', async () => {
    await CastInviteLegacyRoute({ params: Promise.resolve({ token: 'raw-invitation-token' }) });

    const [target] = mockedRedirect.mock.calls[0] as [string];
    expect(target.split('#')[0]).toBe('/platform/invite');
    expect(target.split('#')[0]).not.toContain('raw-invitation-token');
  });

  it('URL で意味を持つ文字を含むトークンも符号化し直す', () => {
    // ルート引数は Next が復号済みなので、載せ直す際に符号化しないと壊れる
    return CastInviteLegacyRoute({ params: Promise.resolve({ token: 'a/b?c#d' }) }).then(() => {
      expect(mockedRedirect).toHaveBeenCalledWith('/platform/invite#a%2Fb%3Fc%23d');
    });
  });
});
