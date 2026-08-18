import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastRequestsPage } from '../CastRequestsPage';
import { shiftApi } from '@/entities/shift';

jest.mock('@/entities/shift', () => ({
  shiftApi: {
    myStores: jest.fn(),
    myShiftRequests: jest.fn(),
    submitShiftRequest: jest.fn(),
  },
}));

const mockedMyStores = shiftApi.myStores as jest.Mock;
const mockedMyShiftRequests = shiftApi.myShiftRequests as jest.Mock;
const mockedSubmit = shiftApi.submitShiftRequest as jest.Mock;

const STORES = [
  { store_id: 1, store_name: '店舗A' },
  { store_id: 2, store_name: '店舗B' },
];

/**
 * 所属店舗の読み込み完了を待つ。Select は form 内で隠し input を併走させ
 * 同じ店舗名を選択中ラベルと option の二箇所に描くため、件数を問わない findAll で待つ。
 */
function waitStoresLoaded() {
  return screen.findAllByText('店舗A');
}

describe('CastRequestsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMyStores.mockResolvedValue(STORES);
    mockedMyShiftRequests.mockResolvedValue({ rows: [], nextCursor: null });
  });

  it('店舗セレクタに所属店舗一覧を表示する', async () => {
    render(<CastRequestsPage />);

    await waitFor(() => expect(mockedMyStores).toHaveBeenCalledTimes(1));
    // 選択肢は開いている間だけ描画される。
    fireEvent.click(await screen.findByRole('combobox', { name: '店舗' }));
    expect(await screen.findByRole('option', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '店舗B' })).toBeInTheDocument();
  });

  it('過去日を指定して提出すると検証エラーを表示し、提出しないこと', async () => {
    render(<CastRequestsPage />);
    // 所属店セレクタの描画完了を待つ（マウント時の非同期読み込みが未解決のまま操作すると
    // store_id の初期値設定と競合するため、react-hook-form の値確定後に操作する）。
    await waitStoresLoaded();

    fireEvent.change(screen.getByLabelText('日付'), { target: { value: '2000-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    expect(await screen.findByText('過去の日付は指定できません')).toBeInTheDocument();
    expect(mockedSubmit).not.toHaveBeenCalled();
  });

  it('暦日の前日は client 側で弾かないこと（日付変更時刻前の深夜帯ではそれがまだ現在の営業日）', async () => {
    mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yyyymmdd = [
      yesterday.getFullYear(),
      String(yesterday.getMonth() + 1).padStart(2, '0'),
      String(yesterday.getDate()).padStart(2, '0'),
    ].join('-');
    fireEvent.change(screen.getByLabelText('日付'), { target: { value: yyyymmdd } });
    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0]).toMatchObject({ work_date: yyyymmdd });
  });

  it('本日の日付は許容され提出されること', async () => {
    mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    const d = new Date();
    const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate()
    ).padStart(2, '0')}`;
    fireEvent.change(screen.getByLabelText('日付'), { target: { value: today } });
    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0]).toMatchObject({ work_date: today });
  });

  it('備考が501文字だと検証エラーを表示し、提出しないこと', async () => {
    render(<CastRequestsPage />);
    await waitStoresLoaded();

    fireEvent.change(screen.getByLabelText('備考'), { target: { value: 'あ'.repeat(501) } });
    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    expect(await screen.findByText('備考は500文字以内で入力してください')).toBeInTheDocument();
    expect(mockedSubmit).not.toHaveBeenCalled();
  });

  it('提出に成功するとフォームをリセットし履歴を再取得すること', async () => {
    mockedSubmit.mockResolvedValue({ id: 'sr1', status: 'PENDING' });
    render(<CastRequestsPage />);
    await waitFor(() => expect(mockedMyStores).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockedMyShiftRequests).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    await waitFor(() => expect(mockedSubmit).toHaveBeenCalledTimes(1));
    expect(mockedSubmit.mock.calls[0][0]).toMatchObject({
      store_id: 1,
      start_time: '18:00:00',
      end_time: '23:00:00',
    });
    await waitFor(() => expect(mockedMyShiftRequests).toHaveBeenCalledTimes(2));
  });

  it('履歴の状態バッジ(受付済み/確定済み/却下)を表示すること', async () => {
    mockedMyShiftRequests.mockResolvedValue({
      rows: [
        {
          id: 'sr1',
          work_date: '2026-08-01',
          start_time: '18:00:00',
          end_time: '23:00:00',
          note: null,
          status: 'PENDING',
          store_id: 1,
          store_name: '店舗A',
          created_at: '2026-07-20T00:00:00Z',
        },
        {
          id: 'sr2',
          work_date: '2026-08-02',
          start_time: '10:00:00',
          end_time: '12:00:00',
          note: null,
          status: 'APPROVED',
          store_id: 2,
          store_name: '店舗B',
          created_at: '2026-07-21T00:00:00Z',
        },
        {
          id: 'sr3',
          work_date: '2026-08-03',
          start_time: '14:00:00',
          end_time: '16:00:00',
          note: null,
          status: 'DECLINED',
          store_id: 1,
          store_name: '店舗A',
          created_at: '2026-07-22T00:00:00Z',
        },
      ],
      nextCursor: null,
    });

    render(<CastRequestsPage />);

    expect(await screen.findByText('受付済み')).toBeInTheDocument();
    expect(screen.getByText('確定済み')).toBeInTheDocument();
    expect(screen.getByText('却下')).toBeInTheDocument();
  });

  it('変更申請の履歴は種別バッジと専用の状態文言(変更承認済み/謝絶)で表示すること', async () => {
    mockedMyShiftRequests.mockResolvedValue({
      rows: [
        {
          id: 'sr4',
          work_date: '2026-08-04',
          start_time: '19:00:00',
          end_time: '22:00:00',
          type: 'CHANGE',
          status: 'APPROVED',
          store_id: 1,
          store_name: '店舗A',
          created_at: '2026-07-23T00:00:00Z',
        },
        {
          id: 'sr5',
          work_date: '2026-08-05',
          start_time: '19:00:00',
          end_time: '22:00:00',
          type: 'CHANGE',
          status: 'DECLINED',
          store_id: 1,
          store_name: '店舗A',
          created_at: '2026-07-24T00:00:00Z',
        },
      ],
      nextCursor: null,
    });

    render(<CastRequestsPage />);

    expect(await screen.findByText('変更承認済み')).toBeInTheDocument();
    expect(screen.getByText('謝絶')).toBeInTheDocument();
    expect(screen.getAllByText('変更申請')).toHaveLength(2);
    expect(screen.queryByText('確定済み')).not.toBeInTheDocument();
    expect(screen.queryByText('却下')).not.toBeInTheDocument();
  });

  it('取得に失敗した場合はエラー文言を表示し、再試行で復帰できること', async () => {
    mockedMyShiftRequests.mockRejectedValueOnce(new Error('network error'));
    mockedMyShiftRequests.mockResolvedValueOnce({ rows: [], nextCursor: null });

    render(<CastRequestsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('履歴の取得に失敗しました')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('提出履歴はありません')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('所属店舗が取れなければ履歴ではなく店舗欄が失敗を名乗り、再試行できること', async () => {
    // 空の候補は placeholder の「所属店舗がありません」と区別がつかない
    mockedMyStores.mockRejectedValueOnce(new Error('network error'));
    mockedMyStores.mockResolvedValueOnce(STORES);

    render(<CastRequestsPage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('所属店舗の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('履歴の取得に失敗しました')).not.toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    await waitStoresLoaded();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('所属店舗の取得中は「所属店舗がありません」と言い切らないこと', async () => {
    mockedMyStores.mockReturnValue(new Promise(() => {}));

    render(<CastRequestsPage />);

    // 履歴側の読み込み表示は先に解けるので、残る読み込み中は店舗欄のもの
    expect(await screen.findByText('提出履歴はありません')).toBeInTheDocument();
    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });
});
