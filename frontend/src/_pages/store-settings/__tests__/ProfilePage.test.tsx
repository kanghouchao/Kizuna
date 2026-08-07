import { StrictMode } from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { StoreProfileResponse, storeProfileApi } from '@/entities/store-profile';
import StoreProfilePage from '../ui/ProfilePage';

jest.mock('@/entities/store-profile', () => ({
  storeProfileApi: { get: jest.fn(), update: jest.fn() },
}));

jest.mock('../ui/StoreProfileForm', () => {
  const React = require('react');
  return {
    // 実体は設定を取得できたときだけ描かれる。mock はマーカーだけ出す。
    StoreProfileForm: () => React.createElement('div', null, '店舗情報フォーム'),
  };
});

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = storeProfileApi as jest.Mocked<typeof storeProfileApi>;

const profile = { id: '1', template_key: 'default' } as StoreProfileResponse;

describe('店舗情報ページの取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('取得に失敗したらフォームを描かず、区画が失敗を名乗って再試行できること', async () => {
    mockedApi.get.mockRejectedValueOnce(new Error('boom'));

    render(<StoreProfilePage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('店舗情報の取得に失敗しました')).toBeInTheDocument();
    // 取れなかった設定でフォームを描くと、保存がその古い値を本当にしてしまう
    expect(screen.queryByText('店舗情報フォーム')).not.toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();

    mockedApi.get.mockResolvedValue(profile);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗情報フォーム')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(mockedApi.get).toHaveBeenCalledTimes(2);
  });

  it('二度目の失敗でも読み込み中を経由し、区画が mount し直されること', async () => {
    mockedApi.get.mockRejectedValueOnce(new Error('boom'));

    render(<StoreProfilePage />);
    const region = await screen.findByRole('alert');

    // 2 回目の解決を保留し、押した直後の姿を観測する
    let failSecond = (): void => {};
    mockedApi.get.mockReturnValueOnce(
      new Promise((_, reject) => {
        failSecond = () => reject(new Error('boom again'));
      })
    );
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    // 読み込み中を経由するので、この時点で区画は一度消えている。消えないまま二度目の失敗を
    // 迎えると role="alert" が再発火せず、読み上げ利用者には何も届かない
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await act(async () => {
      failSecond();
    });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が設定をクリアする
  // ようになった以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  it('二重 mount で古い失敗が後から着いても、新しい成功を消さないこと', async () => {
    let failStale = (): void => {};
    mockedApi.get
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValueOnce(profile);

    render(
      <StrictMode>
        <StoreProfilePage />
      </StrictMode>
    );

    expect(await screen.findByText('店舗情報フォーム')).toBeInTheDocument();

    await act(async () => {
      failStale();
    });

    expect(screen.getByText('店舗情報フォーム')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
