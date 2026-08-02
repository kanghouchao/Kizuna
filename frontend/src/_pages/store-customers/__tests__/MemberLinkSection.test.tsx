import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { MemberLinkSection } from '../ui/MemberLinkSection';
import { customerApi } from '@/entities/customer';

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  toast: { error: jest.fn(), success: jest.fn() },
}));

jest.mock('@/entities/customer', () => ({
  customerApi: {
    linkMember: jest.fn(),
    unlinkMember: jest.fn(),
    memberLinkHistory: jest.fn(),
  },
}));

const mockedApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedToast = toast as unknown as { error: jest.Mock; success: jest.Mock };

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
    expect(mockedToast.success).toHaveBeenCalledWith('会員を紐づけました');
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
      expect(mockedToast.error).toHaveBeenCalledWith('この会員は既に他の顧客と紐づいています')
    );
  });

  it('履歴の取得に失敗している間は未紐づけ表示にせず、紐づけ操作を無効化すること', async () => {
    mockedApi.memberLinkHistory.mockRejectedValueOnce(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ状態を取得できませんでした')).toBeInTheDocument();
    // 取得失敗を「未紐づけ」と断定表示しない（POST は既存の有効区間を置き換えるため）
    expect(screen.queryByText('未紐づけ')).not.toBeInTheDocument();
    expect(screen.queryByText('紐づけ履歴がありません')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeDisabled();
  });

  it('再読み込みで履歴が取れれば操作が解放されること', async () => {
    mockedApi.memberLinkHistory.mockRejectedValueOnce(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);
    fireEvent.click(await screen.findByRole('button', { name: '再読み込み' }));

    expect(await screen.findByText('未紐づけ')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeEnabled();
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
