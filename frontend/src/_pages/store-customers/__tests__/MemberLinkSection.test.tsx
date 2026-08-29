import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { MemberLinkSection } from '../ui/MemberLinkSection';
import { customerApi } from '@/entities/customer';
import { TokenClaims, readTokenClaims } from '@/shared/lib';

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

jest.mock('@/entities/customer', () => ({
  customerApi: {
    linkMember: jest.fn(),
    unlinkMember: jest.fn(),
    memberLink: jest.fn(),
    memberLinkHistory: jest.fn(),
    memberPointBalance: jest.fn(),
    adjustPoints: jest.fn(),
  },
}));

const mockedApi = customerApi as jest.Mocked<typeof customerApi>;
const mockedNotify = notify as unknown as { error: jest.Mock; success: jest.Mock };
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;

/** 指定権限を claim（PERM_ 接頭辞）として持つ token claim を返すヘルパ（UI 出し分けは権限ベース）。 */
function claimsWith(permissions: string[]): TokenClaims {
  return {
    authorities: permissions.map(permission => `PERM_${permission}`),
    userType: 'STAFF',
    storeBridge: true,
  };
}

/** 未紐づけはサーバが 404 で返す（本体で表さない）。 */
const notLinked = { response: { status: 404 } };

/** 現に有効な紐づけの応答。 */
const currentLink = {
  linked: true,
  member_code: '123456789012',
  linked_at: '2026-08-01T10:00:00+09:00',
};

/** 履歴 1 頁分。続きは無い。 */
function historyPage(rows: unknown[]) {
  return { rows, nextCursor: null } as never;
}

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
    mockedApi.memberLink.mockRejectedValue(notLinked);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([]));
    mockedApi.memberPointBalance.mockResolvedValue({ linked: false });
    mockedReadClaims.mockReturnValue(claimsWith(['CUSTOMER_MANAGE']));
  });

  it('紐づけが無ければ未紐づけを示し、履歴も空表示になること', async () => {
    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ履歴がありません')).toBeInTheDocument();
    expect(screen.getByText('未紐づけ')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '解除' })).not.toBeInTheDocument();
  });

  it('ACTIVE の区間があれば紐づけ済みバッジと会員コードを出し、履歴の実行者・日時を表示すること', async () => {
    mockedApi.memberLink.mockResolvedValue(currentLink);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([activeRow, releasedRow]));

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
    // 現況も取り直す（紐づけ先が変わるのはこの読み口が答える事実）
    await waitFor(() => expect(mockedApi.memberLink).toHaveBeenCalledTimes(2));
    // 紐づく先が変われば残高の指す台帳も変わる
    expect(mockedApi.memberPointBalance).toHaveBeenCalledTimes(2);
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

  it('現況が 404 なら未紐づけとして操作を解放すること', async () => {
    // 404 は「取れなかった」ではなく「紐づいていない」— 操作を止める理由にならない
    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('未紐づけ')).toBeInTheDocument();
    expect(screen.queryByText('紐づけ状態は不明です')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '解除' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeEnabled();
  });

  it('現況が 200 なら紐づけ済みとして解除の導線を出すこと', async () => {
    mockedApi.memberLink.mockResolvedValue(currentLink);

    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ済み')).toBeInTheDocument();
    expect(screen.getByText('123456789012')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '解除' })).toBeInTheDocument();
    expect(screen.queryByText('未紐づけ')).not.toBeInTheDocument();
  });

  it('現況の取得に失敗している間は未紐づけ表示にせず、紐づけ操作を無効化すること', async () => {
    // 404 以外の失敗は現況が不明。POST は既存の有効区間を置き換えるため、
    // 未読込のまま操作させると読み取り失敗が誤解除に化ける
    mockedApi.memberLink.mockRejectedValue(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('紐づけ状態は不明です')).toBeInTheDocument();
    expect(screen.queryByText('未紐づけ')).not.toBeInTheDocument();
    expect(mockedNotify.error).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeDisabled();
  });

  it('履歴の取得に失敗しても、現況が読めていれば操作は解放されること', async () => {
    // 履歴と現況は別の読み口。履歴が読めないことは紐づけ操作を止める理由にならない
    mockedApi.memberLinkHistory.mockRejectedValue(new Error('network'));

    render(<MemberLinkSection customerId="c1" />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('会員紐づけの履歴取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('紐づけ履歴がありません')).not.toBeInTheDocument();
    expect(await screen.findByText('未紐づけ')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('会員コード'), {
      target: { value: '123456789012' },
    });
    expect(screen.getByRole('button', { name: '紐づける' })).toBeEnabled();
  });

  it('続きがあれば追加読み込みで履歴を継ぎ足すこと', async () => {
    mockedApi.memberLinkHistory
      .mockResolvedValueOnce({ rows: [activeRow], nextCursor: 'c-2' } as never)
      .mockResolvedValueOnce(historyPage([releasedRow]));

    render(<MemberLinkSection customerId="c1" />);

    fireEvent.click(await screen.findByRole('button', { name: 'さらに読み込む' }));

    expect(await screen.findByText('999999999999')).toBeInTheDocument();
    // 読み込み済みの範囲は読み直さない
    expect(mockedApi.memberLinkHistory).toHaveBeenNthCalledWith(2, 'c1', { cursor: 'c-2' });
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'さらに読み込む' })).not.toBeInTheDocument()
    );
  });

  it('解除は確認ダイアログの実行でのみ API を呼ぶこと', async () => {
    mockedApi.memberLink.mockResolvedValue(currentLink);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([activeRow]));
    mockedApi.unlinkMember.mockResolvedValue(undefined);

    render(<MemberLinkSection customerId="c1" />);

    fireEvent.click(await screen.findByRole('button', { name: '解除' }));
    fireEvent.click(await screen.findByRole('button', { name: 'キャンセル' }));
    expect(mockedApi.unlinkMember).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '解除' }));
    fireEvent.click(await screen.findByRole('button', { name: '解除する' }));

    await waitFor(() => expect(mockedApi.unlinkMember).toHaveBeenCalledWith('c1'));
    // 解除で残高の指す台帳が無くなる
    await waitFor(() => expect(mockedApi.memberPointBalance).toHaveBeenCalledTimes(2));
  });

  it('紐づけ済みなら会員台帳の残高を表示すること', async () => {
    mockedApi.memberLink.mockResolvedValue(currentLink);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([activeRow]));
    mockedApi.memberPointBalance.mockResolvedValue({ linked: true, balance: 120 });

    render(<MemberLinkSection customerId="c1" />);

    expect(await screen.findByText('120 ポイント')).toBeInTheDocument();
  });

  it('未紐づけなら残高の数を出さないこと', async () => {
    render(<MemberLinkSection customerId="c1" />);

    // 台帳そのものが無いので、残高は 0 ではなく「無い」
    expect(await screen.findByText('—')).toBeInTheDocument();
  });

  it('残高を取得している間は、未紐づけの姿を先に出さないこと', async () => {
    // 「まだ読めていない」と「台帳が無い」は別の状態。読み込み中に — を出すと、
    // 到着の 1 フレーム手前で残高が無いと読める
    mockedApi.memberPointBalance.mockReturnValue(new Promise<never>(() => {}));

    render(<MemberLinkSection customerId="c1" />);

    // 履歴は先に着く。残り 1 つの「読み込み中...」は残高のもの
    await screen.findByText('紐づけ履歴がありません');
    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
  });

  it('残高が読めないときは未紐づけの姿にせず、区画が自分で名乗って再試行できること', async () => {
    mockedApi.memberPointBalance.mockRejectedValueOnce(new Error('boom'));

    render(<MemberLinkSection customerId="c1" />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('ポイント残高の取得に失敗しました')).toBeInTheDocument();
    // 読めなかっただけの状態を「台帳が無い」と読ませない
    expect(screen.queryByText('—')).not.toBeInTheDocument();
    expect(mockedNotify.error).not.toHaveBeenCalled();

    mockedApi.memberPointBalance.mockResolvedValue({ linked: true, balance: 80 });
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('80 ポイント')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('POINT_ADJUST を持たなければ調整の導線を出さないこと', async () => {
    mockedApi.memberLink.mockResolvedValue(currentLink);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([activeRow]));
    mockedApi.memberPointBalance.mockResolvedValue({ linked: true, balance: 120 });

    render(<MemberLinkSection customerId="c1" />);

    await screen.findByText('120 ポイント');
    expect(screen.queryByRole('button', { name: 'ポイント調整' })).not.toBeInTheDocument();
  });

  it('紐づけが無ければ、権限があっても調整の導線を出さないこと', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CUSTOMER_MANAGE', 'POINT_ADJUST']));

    render(<MemberLinkSection customerId="c1" />);

    await screen.findByText('紐づけ履歴がありません');
    expect(screen.queryByRole('button', { name: 'ポイント調整' })).not.toBeInTheDocument();
  });

  it('調整に成功したら、取り直さずに残高の表示を差し替えること', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CUSTOMER_MANAGE', 'POINT_ADJUST']));
    mockedApi.memberLink.mockResolvedValue(currentLink);
    mockedApi.memberLinkHistory.mockResolvedValue(historyPage([activeRow]));
    mockedApi.memberPointBalance.mockResolvedValue({ linked: true, balance: 120 });
    mockedApi.adjustPoints.mockResolvedValue({ linked: true, balance: 220 });

    render(<MemberLinkSection customerId="c1" />);

    fireEvent.click(await screen.findByRole('button', { name: 'ポイント調整' }));
    fireEvent.change(await screen.findByLabelText('増減ポイント'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('combobox', { name: '事由の分類' }));
    const category = await screen.findByRole('option', { name: '個別施策' });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(category);
    fireEvent.click(category);
    fireEvent.change(screen.getByLabelText('事由'), { target: { value: '手動付与' } });
    fireEvent.click(screen.getByRole('button', { name: '調整する' }));

    expect(await screen.findByText('220 ポイント')).toBeInTheDocument();
    // 応答が調整後の残高を持つので読み直さない（読み込み表示で一瞬消えない）
    expect(mockedApi.memberPointBalance).toHaveBeenCalledTimes(1);
  });
});
