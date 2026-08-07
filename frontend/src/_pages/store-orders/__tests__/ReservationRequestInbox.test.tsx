import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { ReservationRequestInbox } from '../ui/ReservationRequestInbox';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    listReservationRequests: jest.fn(),
    confirm: jest.fn(),
    decline: jest.fn(),
    listReceptionists: jest.fn(),
    updateReservationRequest: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedList = orderApi.listReservationRequests as jest.Mock;
const mockedConfirm = orderApi.confirm as jest.Mock;
const mockedDecline = orderApi.decline as jest.Mock;

const request = (id: string) => ({
  id,
  status: 'CREATED',
  reception_route: 'WEB',
  business_date: '2026-08-10',
});

/** n 件の申請。窓が満ちているかどうかを見る画面なので、件数は実際の応答と同じ形にする。 */
const requests = (n: number, offset = 0) =>
  Array.from({ length: n }, (_, i) => request(`o${offset + i}`));

const page = (rows: unknown[], nextCursor: string | null = null) => ({ rows, nextCursor });

/**
 * カーソルで続きを返すサーバの代役。位置は「次に返す行の番号」で表す。
 *
 * 位置が件数ではなく行そのものを指すため、手前の行が処理で消えても続きはずれない。
 */
const cursorServer = (total: number) => (params: { cursor?: string; size: number }) => {
  const start = params.cursor ? Number(params.cursor) : 0;
  const end = Math.min(start + params.size, total);
  return Promise.resolve(page(requests(end - start, start), end < total ? String(end) : null));
};

describe('ReservationRequestInbox', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('サーバ側で絞り込まれた申請を表示し、一覧の取得では代替しないこと', async () => {
    mockedList.mockResolvedValue(
      page([{ ...request('web-pending'), requester_member_code: '123456789012' }])
    );

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(1);
    expect(screen.getByText('会員コード: 123456789012')).toBeInTheDocument();
    // 絞り込みは専用読み口の責務。取得するのは 1 回分の窓だけで、手元では選り分けない。
    expect(mockedList).toHaveBeenCalledWith({ cursor: undefined, size: 20 });
  });

  it('確定すると確定 API を呼び、一覧の再取得を促す', async () => {
    mockedList.mockResolvedValue(page([request('o1')]));
    mockedConfirm.mockResolvedValue({});
    const onProcessed = jest.fn();

    render(<ReservationRequestInbox onProcessed={onProcessed} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalledWith('o1'));
    expect(onProcessed).toHaveBeenCalled();
  });

  it('処理した申請は手元から取り除き、一覧を取り直さない', async () => {
    // 処理済みの申請は inbox の対象から外れる。取り直しに行くと、読み込み済みの範囲ぶんの要求を撒く
    mockedList.mockResolvedValue(page([request('o1'), request('o2')]));
    mockedConfirm.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(2));
    fireEvent.click(screen.getAllByRole('button', { name: '確定' })[0]);

    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(1));
    // 未処理の申請は残り、取り直しは起きない
    expect(mockedList).toHaveBeenCalledTimes(1);
  });

  it('編集を押すとその申請の編集モーダルが開き、保存後はその行だけ差し替わる', async () => {
    mockedList.mockResolvedValue(page([{ ...request('o1'), pax: 3 }]));
    (orderApi.listReceptionists as jest.Mock).mockResolvedValue([]);
    (orderApi.updateReservationRequest as jest.Mock).mockResolvedValue({
      ...request('o1'),
      pax: 5,
    });
    const onProcessed = jest.fn();

    render(<ReservationRequestInbox onProcessed={onProcessed} />);

    fireEvent.click(await screen.findByRole('button', { name: '編集' }));
    fireEvent.click(await screen.findByRole('button', { name: '保存する' }));

    await waitFor(() =>
      expect(orderApi.updateReservationRequest).toHaveBeenCalledWith('o1', expect.anything())
    );
    // 編集後も申請は未確定のまま残るので、応答の内容で行を差し替えるだけでよい
    expect(await screen.findByText(/5 名/)).toBeInTheDocument();
    expect(mockedList).toHaveBeenCalledTimes(1);
    expect(onProcessed).toHaveBeenCalled();
  });

  it('謝絶すると謝絶 API を呼ぶ', async () => {
    mockedList.mockResolvedValue(page([request('o1')]));
    mockedDecline.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '謝絶' }));

    await waitFor(() => expect(mockedDecline).toHaveBeenCalledWith('o1'));
  });

  it('確定に失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    mockedList.mockResolvedValue(page([request('o1')]));
    // 指名の再検証は「修正するか謝絶するか」を伝える 400 を返す。汎用文言に潰すと行動できない。
    mockedConfirm.mockRejectedValue({
      response: {
        status: 400,
        data: {
          error: '指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください',
        },
      },
    });

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        '指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください'
      )
    );
  });

  it('サーバの文言が無ければ汎用の失敗文言へ落とす', async () => {
    mockedList.mockResolvedValue(page([request('o1')]));
    mockedConfirm.mockRejectedValue(new Error('network'));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(notify.error).toHaveBeenCalledWith('確定に失敗しました'));
  });

  it('未確定の申請が無ければその旨を表示する', async () => {
    mockedList.mockResolvedValue(page([]));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByText('未確定の予約申請はありません')).toBeInTheDocument();
  });

  it('取得に失敗したら空表示ではなくエラーを出し、再読み込みで復帰できる', async () => {
    // 瞬断を「申請なし」に見せると、店舗が未処理の申請を見落とす
    mockedList.mockRejectedValueOnce(new Error('network'));
    mockedList.mockResolvedValueOnce(page([request('o1')]));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('予約申請を取得できませんでした。');
    expect(screen.queryByText('未確定の予約申請はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
  });

  it('再読み込み中は失敗表示を畳んで読み込み中を出す', async () => {
    // 失敗表示のまま黙って待たせると、押した再読み込みが効いているのか分からない
    mockedList.mockRejectedValueOnce(new Error('network'));
    mockedList.mockReturnValueOnce(new Promise(() => {}));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '再試行' }));

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('どこまで広げても 1 回の取得件数は上限のまま', async () => {
    // 要求サイズ自体を膨らませると、サーバ側の取得上限に当たった時点で以降の申請へ到達できなくなる
    mockedList.mockImplementation(cursorServer(2500));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));
    await waitFor(() => expect(mockedList).toHaveBeenCalledWith({ cursor: '20', size: 20 }));
    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));
    await waitFor(() => expect(mockedList).toHaveBeenCalledWith({ cursor: '40', size: 20 }));

    expect(mockedList.mock.calls.every(([params]) => params.size === 20)).toBe(true);
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(60));
  });

  it('取得件数の上限を超える申請は追加読み込みで辿れ、1 回の操作につき 1 要求で済む', async () => {
    mockedList.mockImplementation(cursorServer(25));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));

    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(25));
    // 読み込み済みの範囲は読み直さない。読み直す実装では、読み込み済みページ数と同数の要求が飛ぶ
    expect(mockedList).toHaveBeenCalledTimes(2);
    expect(mockedList).toHaveBeenNthCalledWith(2, { cursor: '20', size: 20 });
    // 全件に届いたので追加読み込みは消える
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument();
  });

  it('読み込み済みの申請を処理しても、追加読み込みは続きの位置から続けること', async () => {
    // 位置を「何件目か」で持つと、処理で 1 件消えた分だけ後続が繰り上がり境界の申請を飛ばす
    mockedList.mockImplementation(cursorServer(25));
    mockedConfirm.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    await screen.findByRole('button', { name: 'もっと見る' });
    fireEvent.click(screen.getAllByRole('button', { name: '確定' })[0]);
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(19));

    fireEvent.click(screen.getByRole('button', { name: 'もっと見る' }));

    await waitFor(() => expect(mockedList).toHaveBeenNthCalledWith(2, { cursor: '20', size: 20 }));
    // 20 件目（境界）を含めて、未処理の申請がすべて残る
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(24));
  });

  it('表示中をすべて処理し終えても、続きが残っていれば「申請なし」とは言わない', async () => {
    // まだ読んでいない申請がある状態を「申請なし」と見せると、店舗が未処理の申請を見落とす
    mockedList.mockResolvedValue(page([request('o1')], 'next'));
    mockedConfirm.mockResolvedValue({});

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(screen.queryAllByRole('button', { name: '確定' })).toHaveLength(0));
    expect(screen.queryByText('未確定の予約申請はありません')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'もっと見る' })).toBeInTheDocument();
  });

  it('追加読み込みに失敗したら領域ごとエラー態になり、再試行は先頭から取り直す', async () => {
    // 古い行を残すと、読めなかった一覧に前回の内容が居座って「これが最新」に見える。
    // 途中の位置から再開すると、1〜20 件目を欠いた 21 件目以降だけが返る。
    const server = cursorServer(25);
    mockedList.mockImplementation(server);

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);
    await screen.findByRole('button', { name: 'もっと見る' });

    // 続きの取得だけを落とす
    mockedList.mockImplementation(params =>
      params.cursor ? Promise.reject(new Error('network')) : server(params)
    );
    fireEvent.click(screen.getByRole('button', { name: 'もっと見る' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('予約申請を取得できませんでした。');
    // 表示中だった申請も消える — 領域は丸ごとエラー態になる
    expect(screen.queryAllByRole('button', { name: '確定' })).toHaveLength(0);
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument();

    mockedList.mockImplementation(server);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    // 位置も起点へ戻っているので、取り直しは先頭から
    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith({ cursor: undefined, size: 20 })
    );
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(20));
  });
});
