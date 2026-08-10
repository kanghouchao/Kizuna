import { render, screen, fireEvent } from '@testing-library/react';
import { MeProvider, useMe } from '../MeContext';
import { platformAuthApi } from '../../api/platform';
import type { PlatformMeResponse } from '../types';

jest.mock('../../api/platform', () => ({
  platformAuthApi: { me: jest.fn() },
}));

const mockedMe = platformAuthApi.me as jest.MockedFunction<typeof platformAuthApi.me>;

const meFixture: PlatformMeResponse = {
  email: 'tanaka.hanako@example.com',
  display_name: '田中花子',
  store_bridge: false,
  line_linked: false,
};

// 消費者は2役に分ける。表示係（Header 相当）と更新係（アカウント設定ページ相当）で、
// setMe の差し替えが別の消費者へ届くこと — プロフィール更新後に持久 layout 側の
// ヘッダー表示が再取得なしで追随する配線の芯 — を検証する。
function NameProbe() {
  const { me, failure, reload } = useMe();
  return (
    <div>
      <p>{failure !== null ? '取得失敗' : (me?.display_name ?? '読み込み中')}</p>
      <button onClick={() => void reload()}>取り直す</button>
    </div>
  );
}

function UpdateProbe() {
  const { setMe } = useMe();
  return (
    <button onClick={() => setMe({ ...meFixture, display_name: '田中はな子' })}>差し替える</button>
  );
}

describe('MeProvider（/platform/me の共有 seam）', () => {
  beforeEach(() => {
    // clearAllMocks は mockRejectedValueOnce の残り（Once 設定）を消さないため、
    // 実装ごと確実に畳んでから設定し直す。
    mockedMe.mockReset();
    mockedMe.mockResolvedValue(meFixture);
  });

  it('取得した自分の情報を消費者へ配る（取得は provider の1回だけ）', async () => {
    render(
      <MeProvider>
        <NameProbe />
      </MeProvider>
    );

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(mockedMe).toHaveBeenCalledTimes(1);
  });

  it('setMe の差し替えは別の消費者へ再取得なしで届く', async () => {
    render(
      <MeProvider>
        <NameProbe />
        <UpdateProbe />
      </MeProvider>
    );
    await screen.findByText('田中花子');

    fireEvent.click(screen.getByRole('button', { name: '差し替える' }));

    expect(await screen.findByText('田中はな子')).toBeInTheDocument();
    expect(mockedMe).toHaveBeenCalledTimes(1);
  });

  it('取得失敗は failure で配られ、reload で取り直せる', async () => {
    mockedMe.mockRejectedValueOnce(new Error('network'));

    render(
      <MeProvider>
        <NameProbe />
      </MeProvider>
    );

    expect(await screen.findByText('取得失敗')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '取り直す' }));

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(screen.queryByText('取得失敗')).not.toBeInTheDocument();
  });
});
