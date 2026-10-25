import { render, screen } from '@testing-library/react';
import { redirect } from 'next/navigation';
import { serverClient } from '@/shared/api/index.server';
import { InitialSetupPage } from '../ui/InitialSetupPage';

jest.mock('@/shared/api/server-client', () => ({
  serverClient: { get: jest.fn() },
}));

// 本物の redirect と同じく例外で抜ける。try/catch の中で呼ぶ実装はこれを飲み込んで画面を描いてしまう
jest.mock('next/navigation', () => ({
  redirect: jest.fn(() => {
    throw new Error('NEXT_REDIRECT');
  }),
  useRouter: () => ({ push: jest.fn() }),
}));

const mockedGet = serverClient.get as jest.Mock;
const mockedRedirect = redirect as unknown as jest.Mock;

describe('InitialSetupPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('完了済みならログイン画面へリダイレクトする', async () => {
    mockedGet.mockResolvedValue({ required: false });

    await expect(InitialSetupPage()).rejects.toThrow('NEXT_REDIRECT');

    expect(mockedRedirect).toHaveBeenCalledWith('/auth/login');
  });

  it('未完了なら登録フォームを表示する（要否は毎回取り直す）', async () => {
    mockedGet.mockResolvedValue({ required: true });

    render(await InitialSetupPage());

    expect(screen.getByText('初期設定')).toBeInTheDocument();
    expect(screen.getByText('最初の管理者アカウントを登録してください')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledWith('/auth/initial-setup', { cache: 'no-store' });
    expect(mockedRedirect).not.toHaveBeenCalled();
  });

  it('要否を取得できなくても登録フォームを表示する', async () => {
    // 完了後の登録は送信先が 404 で拒むため、取得の失敗で入口を閉ざさない
    mockedGet.mockRejectedValue(new Error('APIエラー: 503 Service Unavailable'));
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

    render(await InitialSetupPage());

    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument();
    expect(mockedRedirect).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
  });
});
