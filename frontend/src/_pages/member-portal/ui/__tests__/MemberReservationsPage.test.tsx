import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemberReservationsPage } from '../MemberReservationsPage';
import { memberOrderApi } from '@/entities/order';
import { notify } from '@/shared/notify';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  memberOrderApi: { list: jest.fn(), cancel: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedList = memberOrderApi.list as jest.Mock;
const mockedCancel = memberOrderApi.cancel as jest.Mock;

const page = (rows: unknown[], nextCursor: string | null = null) => ({ rows, nextCursor });

/** カーソルで続きを返すサーバの代役。位置は「次に返す行の番号」で表す。 */
const cursorServer = (total: number) => (params: { cursor?: string; size: number }) => {
  const start = params.cursor ? Number(params.cursor) : 0;
  const end = Math.min(start + params.size, total);
  const rows = Array.from({ length: end - start }, (_, i) => ({
    id: `o${start + i}`,
    store_name: `店舗${start + i}`,
    business_date: '2026-08-10',
    status: 'CREATED',
  }));
  return Promise.resolve(page(rows, end < total ? String(end) : null));
};

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

  it('取り下げると本人取り下げの API を呼び、その行だけ差し替える', async () => {
    mockedList.mockResolvedValue(
      page([{ id: 'o1', store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' }])
    );
    mockedCancel.mockResolvedValue({
      id: 'o1',
      store_name: '店舗A',
      business_date: '2026-08-10',
      status: 'CANCELLED',
    });

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() => expect(mockedCancel).toHaveBeenCalledWith('o1'));
    // 取り下げても予約は一覧に残る（状態が変わるだけ）ので、取り直しに行く必要がない
    expect(await screen.findByText('キャンセル')).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledTimes(1);
  });

  it('識別子の無い予約には取り下げの要求を組まず、理由を名乗ること', async () => {
    // `?? ''` で素通しすると POST /platform/me/orders//cancellation が飛び、届いた先の 404 が
    // 「取り下げに失敗しました」と見分けが付かなくなる
    mockedList.mockResolvedValue(
      page([{ store_name: '店舗A', business_date: '2026-08-10', status: 'CREATED' }])
    );

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: '取り下げる' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        expect.stringContaining('予約の識別子が取得できていません')
      )
    );
    expect(mockedCancel).not.toHaveBeenCalled();
  });

  it('取得に失敗したら領域エラー態を出し、再試行で復帰できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockImplementation(cursorServer(1));

    render(<MemberReservationsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    expect(screen.queryByText('予約はまだありません。')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('追加読み込みに失敗したら領域ごとエラー態になり、再試行は先頭から取り直す', async () => {
    // 古い行を残すと、読めなかった一覧に前回の内容が居座って「これが最新」に見える。
    // 途中の位置から再開すると、1〜20 件目を欠いた 21 件目以降だけが返る。
    const server = cursorServer(25);
    mockedList.mockImplementation(server);

    render(<MemberReservationsPage />);
    await screen.findByRole('button', { name: 'もっと見る' });

    // 続きの取得だけを落とす
    mockedList.mockImplementation(params =>
      params.cursor ? Promise.reject(new Error('failed')) : server(params)
    );
    fireEvent.click(screen.getByRole('button', { name: 'もっと見る' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    // 表示中だった予約も消える — 領域は丸ごとエラー態になる
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '取り下げる' })).toHaveLength(0);
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument();

    mockedList.mockImplementation(server);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    // 位置も起点へ戻っているので、取り直しは先頭から
    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith({ cursor: undefined, size: 20 })
    );
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(20)
    );
  });

  it('どこまで広げても 1 回の取得件数は上限のまま', async () => {
    // 要求サイズ自体を膨らませると、サーバ側の取得上限に当たった時点で以降の予約へ到達できなくなる
    mockedList.mockImplementation(cursorServer(2500));

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));
    await waitFor(() => expect(mockedList).toHaveBeenCalledWith({ cursor: '20', size: 20 }));

    expect(mockedList.mock.calls.every(([params]) => params.size === 20)).toBe(true);
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(40)
    );
  });

  it('追加読み込みは、読み込み済みの量によらず 1 回の操作につき 1 要求で済む', async () => {
    mockedList.mockImplementation(cursorServer(45));

    render(<MemberReservationsPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(40)
    );
    fireEvent.click(screen.getByRole('button', { name: 'もっと見る' }));
    await waitFor(() =>
      expect(screen.getAllByRole('button', { name: '取り下げる' })).toHaveLength(45)
    );

    // 読み込み済みの範囲を読み直す実装では、ここまでで 1+2+3=6 要求が飛ぶ
    expect(mockedList).toHaveBeenCalledTimes(3);
  });
});
