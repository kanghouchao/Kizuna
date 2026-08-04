import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationsPage } from '../MemberReservationsPage';
import { memberOrderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  memberOrderApi: { list: jest.fn(), cancel: jest.fn() },
}));

const mockedList = memberOrderApi.list as jest.Mock;
const mockedCancel = memberOrderApi.cancel as jest.Mock;

const page = (rows: unknown[]) => ({ rows, page: 0, pageCount: 1, total: rows.length });

describe('MemberReservationsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('全店舗の予約を状態つきで表示する', async () => {
    mockedList.mockResolvedValue(
      page([
        { id: 'o1', store_name: '店舗A', business_date: '2026-08-10', pax: 2, status: 'CREATED' },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          cast_name: 'さくら',
          status: 'CONFIRMED',
        },
      ])
    );

    render(<MemberReservationsPage />);

    expect(await screen.findByText('店舗A')).toBeInTheDocument();
    expect(screen.getByText('店舗B')).toBeInTheDocument();
    expect(screen.getByText('申請中')).toBeInTheDocument();
    expect(screen.getByText('確定')).toBeInTheDocument();
  });

  it('取り下げボタンは確定前の予約にだけ出す', async () => {
    mockedList.mockResolvedValue(
      page([
        { id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' },
        { id: 'o2', store_name: '店舗B', business_date: '2026-08-11', status: 'CONFIRMED' },
      ])
    );

    render(<MemberReservationsPage />);

    await screen.findByText('店舗A');
    expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(1);
  });

  it('取り下げると本人取り下げの API を呼び、一覧を取り直す', async () => {
    mockedList.mockResolvedValue(
      page([{ id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' }])
    );
    mockedCancel.mockResolvedValue({});

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() => expect(mockedCancel).toHaveBeenCalledWith('o1'));
    expect(mockedList).toHaveBeenCalledTimes(2);
  });

  it('取り下げ後の取り直しが届くまで、表示中の行の取り下げを受け付けない', async () => {
    // 行は残したままだが、古い行を押せると済んだ取り下げをもう一度投げてしまう
    mockedList.mockResolvedValueOnce(
      page([{ id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' }])
    );
    mockedCancel.mockResolvedValue({});
    mockedList.mockReturnValueOnce(new Promise(() => {}));

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    // 取り直しが始まった＝processingId は既にクリアされている。ここから先が観測したい窓。
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('button', { name: '取り下げる' })).toBeDisabled();
    // 行そのものは消さない
    expect(screen.getByText('店舗A')).toBeInTheDocument();
  });

  it('取得に失敗したらエラーメッセージを表示する', async () => {
    mockedList.mockRejectedValue(new Error('failed'));

    render(<MemberReservationsPage />);

    expect(
      await screen.findByText('予約を取得できませんでした。再読み込みしてください。')
    ).toBeInTheDocument();
  });

  it('追加読み込みに失敗しても既に読み込んだ予約は消さず、その拡張だけ再試行できる', async () => {
    // 窓が満ちているかどうかを見る画面なので、件数は実際の応答と同じ形にする
    const reservations = (n: number) =>
      Array.from({ length: n }, (_, i) => ({
        id: `o${i}`,
        store_name: i === 0 ? '店舗A' : `店舗${i}`,
        business_date: '2026-08-10',
        status: 'CREATED',
      }));
    mockedList.mockResolvedValueOnce({ rows: reservations(20), page: 0, pageCount: 2, total: 25 });
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockResolvedValueOnce({ rows: reservations(25), page: 0, pageCount: 2, total: 25 });

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));

    expect(
      await screen.findByText('予約を追加で取得できませんでした。表示は前回の取得内容です。')
    ).toBeInTheDocument();
    // 全体を失敗表示に置き換えない — 既に読み込めていた予約は取り下げられるままにする
    expect(screen.getByText('店舗A')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(20);
    expect(
      screen.queryByText('予約を取得できませんでした。再読み込みしてください。')
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    // 失敗した拡張と同じ範囲を取り直す（読み込み済みページ数を進めていない）
    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 40 }));
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(25)
    );
  });
});
