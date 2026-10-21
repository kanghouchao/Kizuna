import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { StoreProfileResponse, storeProfileApi } from '@/entities/store-profile';
import { useStoreContext } from '@/entities/user';
import StoreProfilePage from '../ui/ProfilePage';

jest.mock('@/entities/store-profile', () => ({
  storeProfileApi: { get: jest.fn(), update: jest.fn(), activate: jest.fn() },
}));

jest.mock('@/entities/user', () => ({
  useStoreContext: jest.fn(),
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
const mockedUseStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockReloadStores = jest.fn();

function withCurrentStatus(status: 'PREPARING' | 'ACTIVE') {
  const store = { id: 1, name: '本店', status };
  mockedUseStoreContext.mockReturnValue({
    stores: [store],
    currentStoreId: '1',
    currentStore: store,
    loadFailed: false,
    reload: mockReloadStores,
    switchStore: jest.fn(),
  });
}

beforeEach(() => withCurrentStatus('ACTIVE'));

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

  it('応答が空でも白紙にせず、区画が失敗を名乗って再試行できること', async () => {
    // 例外にならない空応答（204 や本文なし）でも描くものは無い。白紙で返すと、失敗の告知も
    // 再試行の導線も同時に消える
    mockedApi.get.mockResolvedValueOnce(undefined as unknown as StoreProfileResponse);

    render(<StoreProfilePage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('店舗情報の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('店舗情報フォーム')).not.toBeInTheDocument();

    mockedApi.get.mockResolvedValue(profile);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗情報フォーム')).toBeInTheDocument();
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
});

describe('店舗の稼働状態', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.get.mockResolvedValue(profile);
  });

  const statusRegion = async () => screen.findByRole('region', { name: '稼働状態' });

  it('稼働中の店舗は稼働中と出し、状態を変える操作を持たない', async () => {
    withCurrentStatus('ACTIVE');

    render(<StoreProfilePage />);
    const region = await statusRegion();

    expect(within(region).getByText('稼働中')).toBeInTheDocument();
    expect(within(region).queryByRole('button')).not.toBeInTheDocument();
  });

  it('準備中の店舗は確認を経て稼働中にし、店舗コンテキストを取り直す', async () => {
    withCurrentStatus('PREPARING');
    mockedApi.activate.mockResolvedValue(undefined);

    render(<StoreProfilePage />);
    const region = await statusRegion();
    expect(within(region).getByText('準備中')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '稼働中にする' }));
    // 確認前には送らない
    expect(mockedApi.activate).not.toHaveBeenCalled();
    const dialog = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '稼働中にする' }));

    await waitFor(() => expect(notify.success).toHaveBeenCalledWith('店舗を稼働中にしました'));
    expect(mockedApi.activate).toHaveBeenCalledTimes(1);
    expect(mockReloadStores).toHaveBeenCalledTimes(1);
  });

  it('確認を取り消せば送らない', async () => {
    withCurrentStatus('PREPARING');

    render(<StoreProfilePage />);
    fireEvent.click(within(await statusRegion()).getByRole('button', { name: '稼働中にする' }));
    const dialog = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(mockedApi.activate).not.toHaveBeenCalled();
  });

  it('失敗したら失敗を告げ、店舗コンテキストは取り直さない', async () => {
    withCurrentStatus('PREPARING');
    mockedApi.activate.mockRejectedValue(new Error('boom'));

    render(<StoreProfilePage />);
    fireEvent.click(within(await statusRegion()).getByRole('button', { name: '稼働中にする' }));
    const dialog = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialog).getByRole('button', { name: '稼働中にする' }));

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('稼働状態の変更に失敗しました'));
    expect(mockReloadStores).not.toHaveBeenCalled();
    expect(notify.success).not.toHaveBeenCalled();
  });
});
