import { StrictMode } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { MemberLinkSection } from '../ui/MemberLinkSection';
import { customerApi } from '@/entities/customer';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('@/entities/customer', () => ({
  customerApi: {
    linkMember: jest.fn(),
    unlinkMember: jest.fn(),
    memberLinkHistory: jest.fn(),
  },
}));

const mockedApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedNotify = notify as unknown as { error: jest.Mock; success: jest.Mock };

const activeRow = {
  id: 'l2',
  member_code: '123456789012',
  status: 'ACTIVE' as const,
  linked_at: '2026-08-01T10:00:00+09:00',
  linked_by_name: '山田次郎',
};

const releasedRow = {
  id: 'l1',
  member_code: '999999999999',
  status: 'RELEASED' as const,
  linked_at: '2026-07-01T10:00:00+09:00',
  linked_by_name: '田中花子',
  released_at: '2026-07-15T10:00:00+09:00',
  released_by_name: '山田次郎',
};

describe('MemberLinkSection', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.memberLinkHistory.mockResolvedValue([]);
  });

  it('紐づけが無ければ未紐づけを示し、履歴も空表示になること', async () => {
    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ履歴がありません')).toBeInTheDocument();
    expect(screen.getByText('未紐づけ')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '解除' })).not.toBeInTheDocument();
  });

  it('ACTIVE の区間があれば紐づけ済みバッジと会員コードを出し、履歴の実行者・日時を表示すること', async () => {
    mockedApi.memberLinkHistory.mockResolvedValue([activeRow, releasedRow]);

    render(<MemberLinkSection customerId="c1" />);

    // 状態行と履歴行の 2 箇所に出る
    expect(await screen.findAllByText('紐づけ済み')).toHaveLength(2);
    expect(screen.getAllByText('123456789012')).toHaveLength(2);
    expect(screen.getByText('999999999999')).toBeInTheDocument();
    expect(screen.getByText('解除済み')).toBeInTheDocument();
    // 実行者名は紐づけ・解除の両列に出る
    expect(screen.getByText(/田中花子・/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '解除' })).toBeInTheDocument();
  });

  it('会員コードを入力して紐づけると API を呼び、履歴を取り直すこと', async () => {
    mockedApi.linkMember.mockResolvedValue({ linked: true, member_code: '123456789012' });

    render(<MemberLinkSection customerId="c1" />);
    await screen.findByText('紐づけ履歴がありません');

    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    fireEvent.click(screen.getByRole('button', { name: '紐づける' }));

    await waitFor(() => expect(mockedApi.linkMember).toHaveBeenCalledWith('c1', '123456789012'));
    // 初回ロード + 紐づけ後の取り直し
    await waitFor(() => expect(mockedApi.memberLinkHistory).toHaveBeenCalledTimes(2));
    expect(mockedNotify.success).toHaveBeenCalledWith('会員を紐づけました');
  });

  it('409 のサーバー文言をそのまま toast に出すこと', async () => {
    mockedApi.linkMember.mockRejectedValue({
      response: { status: 409, data: { error: 'この会員は既に他の顧客と紐づいています' } },
    });

    render(<MemberLinkSection customerId="c1" />);
    await screen.findByText('紐づけ履歴がありません');

    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    fireEvent.click(screen.getByRole('button', { name: '紐づける' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('この会員は既に他の顧客と紐づいています')
    );
  });

  it('履歴の取得に失敗している間は未紐づけ表示にせず、紐づけ操作を無効化すること', async () => {
    mockedApi.memberLinkHistory.mockRejectedValueOnce(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ状態は不明です')).toBeInTheDocument();
    // 取得失敗を「未紐づけ」と断定表示しない（POST は既存の有効区間を置き換えるため）
    expect(screen.queryByText('未紐づけ')).not.toBeInTheDocument();
    expect(screen.queryByText('紐づけ履歴がありません')).not.toBeInTheDocument();
    // 失敗を名乗るのは区画自身。横断幕は飛ばさず、区画の中でも二度言わない
    const region = screen.getByRole('alert');
    expect(within(region).getByText('会員紐づけの履歴取得に失敗しました')).toBeInTheDocument();
    expect(within(region).getByRole('button', { name: '再試行' })).toBeInTheDocument();
    expect(mockedNotify.error).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeDisabled();
  });

  it('再試行で履歴が取れれば操作が解放されること', async () => {
    mockedApi.memberLinkHistory.mockRejectedValueOnce(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);
    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

    expect(await screen.findByText('未紐づけ')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeEnabled();
  });

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が履歴をクリアする
  // 以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  it('二重 mount で古い失敗が後から着いても、新しい成功を消さないこと', async () => {
    let failStale = (): void => {};
    mockedApi.memberLinkHistory
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue([activeRow]);

    render(
      <StrictMode>
        <MemberLinkSection customerId="c1" />
      </StrictMode>
    );

    // 解除ボタンは ACTIVE の区間が読めているときだけ出る
    expect(await screen.findByRole('button', { name: '解除' })).toBeInTheDocument();

    await act(async () => {
      failStale();
    });

    expect(screen.getByRole('button', { name: '解除' })).toBeInTheDocument();
    expect(screen.queryByText('会員紐づけの履歴取得に失敗しました')).not.toBeInTheDocument();
  });

  it('解除は確認ダイアログの実行でのみ API を呼ぶこと', async () => {
    mockedApi.memberLinkHistory.mockResolvedValue([activeRow]);
    mockedApi.unlinkMember.mockResolvedValue(undefined);

    render(<MemberLinkSection customerId="c1" />);

    fireEvent.click(await screen.findByRole('button', { name: '解除' }));
    fireEvent.click(await screen.findByRole('button', { name: 'キャンセル' }));
    expect(mockedApi.unlinkMember).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '解除' }));
    fireEvent.click(await screen.findByRole('button', { name: '解除する' }));

    await waitFor(() => expect(mockedApi.unlinkMember).toHaveBeenCalledWith('c1'));
  });
});
