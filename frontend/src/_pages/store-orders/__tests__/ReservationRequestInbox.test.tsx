import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { ReservationRequestInbox } from '../ui/ReservationRequestInbox';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: { listReservationRequests: jest.fn(), confirm: jest.fn(), decline: jest.fn() },
}));

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
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

const page = (rows: unknown[], total = rows.length) => ({
  rows,
  page: 0,
  pageCount: 1,
  total,
});

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
    // 絞り込みは専用読み口の責務。取得するのはページの窓だけで、手元では選り分けない。
    expect(mockedList).toHaveBeenCalledWith({ page: 0, size: 20 });
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

  it('処理後の取り直しが届くまで、表示中の行の確定・謝絶を受け付けない', async () => {
    // 行は残したまま（見失わせないため）だが、古い行を押せると済んだ遷移をもう一度投げてしまう
    mockedList.mockResolvedValueOnce(page([request('o1')]));
    mockedConfirm.mockResolvedValue({});
    mockedList.mockReturnValueOnce(new Promise(() => {}));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    // 取り直しが始まった＝processingId は既にクリアされている。ここから先が観測したい窓。
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('button', { name: '確定' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '謝絶' })).toBeDisabled();
    // 行そのものは消さない
    expect(screen.getByText('2026-08-10')).toBeInTheDocument();
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
      expect(toast.error).toHaveBeenCalledWith(
        '指名キャストが在籍中でないため確定できません。内容を修正するか謝絶してください'
      )
    );
  });

  it('サーバの文言が無ければ汎用の失敗文言へ落とす', async () => {
    mockedList.mockResolvedValue(page([request('o1')]));
    mockedConfirm.mockRejectedValue(new Error('network'));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '確定' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('確定に失敗しました'));
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

    expect(await screen.findByText('予約申請を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByText('未確定の予約申請はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再読み込み' }));

    expect(await screen.findByText('2026-08-10')).toBeInTheDocument();
  });

  it('再読み込み中は失敗表示を畳んで読み込み中を出す', async () => {
    // 失敗表示のまま黙って待たせると、押した再読み込みが効いているのか分からない
    mockedList.mockRejectedValueOnce(new Error('network'));
    mockedList.mockReturnValueOnce(new Promise(() => {}));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '再読み込み' }));

    expect(await screen.findByText('読み込み中...')).toBeInTheDocument();
    expect(screen.queryByText('予約申請を取得できませんでした。')).not.toBeInTheDocument();
  });

  it('サーバ側の上限に当たったら「もっと見る」を出さない', async () => {
    // 要求より少ない件数しか返らないのに残りがある状態で出し続けると、押しても増えないボタンになる
    mockedList.mockResolvedValueOnce(page(requests(20), 2500));
    // 40 件要求したのに 20 件しか返らない＝サーバ側の上限に当たっている
    mockedList.mockResolvedValueOnce(page(requests(20), 2500));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));

    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 40 }));
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument()
    );
  });

  it('取得件数の上限を超える申請は追加読み込みで辿れる', async () => {
    mockedList.mockResolvedValueOnce(page(requests(20), 25));
    mockedList.mockResolvedValueOnce(page(requests(25), 25));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));

    // 継ぎ足しではなく読み込み済みの範囲ごと取り直す（処理で 1 件消えた分の繰り上がりで申請を飛ばさない）
    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 40 }));
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(25));
    // 全件に届いたので追加読み込みは消える
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument();
  });

  it('追加読み込みに失敗しても既に読み込んだ申請は消さず、その拡張だけ再試行できる', async () => {
    mockedList.mockResolvedValueOnce(page(requests(20), 25));
    mockedList.mockRejectedValueOnce(new Error('network'));
    mockedList.mockResolvedValueOnce(page(requests(25), 25));

    render(<ReservationRequestInbox onProcessed={jest.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: 'もっと見る' }));

    expect(
      await screen.findByText('予約申請を取得できませんでした。表示は前回の取得内容です。')
    ).toBeInTheDocument();
    // 見えていた未処理の申請が消えない
    expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(20);

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    // 失敗した拡張と同じ範囲を取り直す（読み込み済みページ数を進めていない）
    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith({ page: 0, size: 40 }));
    await waitFor(() => expect(screen.getAllByRole('button', { name: '確定' })).toHaveLength(25));
  });
});
