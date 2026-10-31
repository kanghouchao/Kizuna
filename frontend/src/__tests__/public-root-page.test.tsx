import { notFound, redirect } from 'next/navigation';
import { resolveHost } from '@/shared/api/index.server';
import Home from '@/app/(public)/page';

const cookieValues = new Map<string, string>();
jest.mock('next/headers', () => ({
  cookies: jest.fn(() =>
    Promise.resolve({
      get: (name: string) => {
        const value = cookieValues.get(name);
        return value === undefined ? undefined : { value };
      },
    })
  ),
}));
jest.mock('next/navigation', () => ({
  notFound: jest.fn(() => {
    throw new Error('NEXT_NOT_FOUND');
  }),
  redirect: jest.fn((to: string) => {
    throw new Error(`NEXT_REDIRECT ${to}`);
  }),
}));
jest.mock('@/shared/api/index.server', () => ({ resolveHost: jest.fn() }));
jest.mock('@/_pages/store-site', () => ({
  StoreSitePage: () => null,
  storeSiteMetadata: jest.fn(),
}));

describe('公開ルート /', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    cookieValues.clear();
  });

  it('未登録の Host はセッションがあっても遷移せず 404 にする', async () => {
    jest.mocked(resolveHost).mockResolvedValue({ kind: 'unknown' });
    cookieValues.set('session-kind', 'member');

    await expect(Home()).rejects.toThrow('NEXT_NOT_FOUND');
    expect(redirect).not.toHaveBeenCalled();
  });

  it('店舗ドメインでも会員のセッションがあれば会員ポータルへ遷移する', async () => {
    jest
      .mocked(resolveHost)
      .mockResolvedValue({ kind: 'store', store: { id: '7', name: '店舗A' } });
    cookieValues.set('session-kind', 'member');

    await expect(Home()).rejects.toThrow('NEXT_REDIRECT /member/');
    expect(notFound).not.toHaveBeenCalled();
  });

  it('アプリドメインで未ログインならログインへ遷移する', async () => {
    jest.mocked(resolveHost).mockResolvedValue({ kind: 'app' });

    await expect(Home()).rejects.toThrow('NEXT_REDIRECT /auth/login');
  });
});
