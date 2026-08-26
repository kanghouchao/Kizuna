import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StoreManagerResponse, storeManagerApi } from '@/entities/user';
import { notify } from '@/shared/notify';
import { StoreManagerSection } from '../ui/StoreManagerSection';

jest.mock('@/entities/user', () => ({
  storeManagerApi: { list: jest.fn(), dismiss: jest.fn() },
}));

// 任命モーダルは開くまで mount されないため、mock は mount = 表示として描画する
jest.mock('@/features/staff-management', () => {
  const React = require('react');
  return {
    StoreManagerAppointModal: ({ storeId }: { storeId: string }) =>
      React.createElement('div', null, `任命モーダル:${storeId}`),
  };
});

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = storeManagerApi as jest.Mocked<typeof storeManagerApi>;

const manager = (override: Partial<StoreManagerResponse> = {}): StoreManagerResponse => ({
  id: 1,
  email: 'manager@example.com',
  display_name: '田中花子',
  enabled: true,
  ...override,
});

beforeEach(() => {
  jest.clearAllMocks();
  mockedApi.list.mockResolvedValue([]);
});

describe('店長設定の節', () => {
  // 店舗の基本情報フォームと同じ画面に並ぶので、見出しに紐づく名前付き領域として名乗る。
  // e2e の絞り込みもこの名前を起点にしている。
  it('見出しに紐づく名前付き領域として名乗ること', async () => {
    render(<StoreManagerSection storeId="1" />);

    expect(await screen.findByRole('region', { name: '店長設定' })).toBeInTheDocument();
  });

  it('店長が 1 人も居なければ未設の注意喚起を出すこと', async () => {
    render(<StoreManagerSection storeId="1" />);

    expect(await screen.findByText('この店舗には店長が設定されていません')).toBeInTheDocument();
  });

  it('店長が居れば氏名とメールを並べ、注意喚起は出さないこと', async () => {
    mockedApi.list.mockResolvedValue([manager()]);

    render(<StoreManagerSection storeId="1" />);

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(screen.getByText('manager@example.com')).toBeInTheDocument();
    expect(screen.queryByText('この店舗には店長が設定されていません')).not.toBeInTheDocument();
  });

  // 停止中の店長は着任していても何も操作できない。行は残しつつ「実質不在」として警告する。
  it('停止中の店長しか居なければ、行は出しつつ未設の注意喚起も出すこと', async () => {
    mockedApi.list.mockResolvedValue([manager({ enabled: false })]);

    render(<StoreManagerSection storeId="1" />);

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
    expect(screen.getByText('停止中')).toBeInTheDocument();
    expect(screen.getByText('この店舗には店長が設定されていません')).toBeInTheDocument();
  });

  it('解任は確認してから実行し、成功後に一覧を取り直すこと', async () => {
    mockedApi.list.mockResolvedValue([manager()]);
    mockedApi.dismiss.mockResolvedValue(undefined);

    render(<StoreManagerSection storeId="1" />);
    fireEvent.click(await screen.findByRole('button', { name: '解任' }));

    expect(await screen.findByText('店長を解任しますか？')).toBeInTheDocument();
    expect(mockedApi.dismiss).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '解任する' }));

    await waitFor(() => expect(mockedApi.dismiss).toHaveBeenCalledWith('1', 1));
    expect(notify.success).toHaveBeenCalledWith('店長を解任しました');
    await waitFor(() => expect(mockedApi.list).toHaveBeenCalledTimes(2));
  });

  // 解任できない理由（最後の担当店舗・全店舗担当）と誘導先はサーバだけが持つので、そのまま出す。
  it('解任の拒否はサーバの文言をそのまま通知すること', async () => {
    mockedApi.list.mockResolvedValue([manager()]);
    mockedApi.dismiss.mockRejectedValue({
      response: { data: { error: '最後の担当店舗のため解任できません。店舗スタッフ管理で…' } },
    });

    render(<StoreManagerSection storeId="1" />);
    fireEvent.click(await screen.findByRole('button', { name: '解任' }));
    fireEvent.click(await screen.findByRole('button', { name: '解任する' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        '最後の担当店舗のため解任できません。店舗スタッフ管理で…'
      )
    );
  });

  it('取得に失敗した領域は自分で名乗り、再試行を置くこと', async () => {
    mockedApi.list.mockRejectedValueOnce(new Error('boom'));

    render(<StoreManagerSection storeId="1" />);

    expect(await screen.findByText('店長の取得に失敗しました')).toBeInTheDocument();
    mockedApi.list.mockResolvedValue([manager()]);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('田中花子')).toBeInTheDocument();
  });

  it('任命モーダルは押すまで mount しないこと', async () => {
    render(<StoreManagerSection storeId="1" />);
    await screen.findByText('この店舗には店長が設定されていません');

    expect(screen.queryByText('任命モーダル:1')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /店長を任命/ }));

    expect(screen.getByText('任命モーダル:1')).toBeInTheDocument();
  });
});
