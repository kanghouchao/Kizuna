import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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

describe('CastRequestsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMyStores.mockResolvedValue(STORES);
    mockedMyShiftRequests.mockResolvedValue([]);
  });

  it('店舗セレクタに所属店舗一覧を表示する', async () => {
    render(<CastRequestsPage />);

    await waitFor(() => expect(mockedMyStores).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole('option', { name: '店舗A' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '店舗B' })).toBeInTheDocument();
  });

  it('過去日を指定して提出すると検証エラーを表示し、提出しないこと', async () => {
    render(<CastRequestsPage />);
    // 所属店セレクタの描画完了を待つ（マウント時の非同期読み込みが未解決のまま操作すると
    // store_id の初期値設定と競合するため、react-hook-form の値確定後に操作する）。
    await screen.findByRole('option', { name: '店舗A' });

    fireEvent.change(screen.getByLabelText('日付'), { target: { value: '2000-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: '提出する' }));

    expect(await screen.findByText('本日以降の日付を指定してください')).toBeInTheDocument();
    expect(mockedSubmit).not.toHaveBeenCalled();
  });

  it('備考が501文字だと検証エラーを表示し、提出しないこと', async () => {
    render(<CastRequestsPage />);
    await screen.findByRole('option', { name: '店舗A' });

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
    mockedMyShiftRequests.mockResolvedValue([
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
    ]);

    render(<CastRequestsPage />);

    expect(await screen.findByText('受付済み')).toBeInTheDocument();
    expect(screen.getByText('確定済み')).toBeInTheDocument();
    expect(screen.getByText('却下')).toBeInTheDocument();
  });

  it('取得に失敗した場合はエラー文言を表示すること', async () => {
    mockedMyShiftRequests.mockRejectedValue(new Error('network error'));

    render(<CastRequestsPage />);

    expect(await screen.findByText('履歴の取得に失敗しました')).toBeInTheDocument();
  });
});
