import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberReservationsPage } from '../MemberReservationsPage';
import { memberOrderApplicationApi } from '@/entities/order';
import { notify } from '@/shared/notify';
import { ClientDataError } from '@/shared/lib';

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order/model/types'),
  memberOrderApplicationApi: { list: jest.fn(), withdraw: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedList = memberOrderApplicationApi.list as jest.Mock;
const mockedWithdraw = memberOrderApplicationApi.withdraw as jest.Mock;

const PAGE_SIZE = 20;

// 年の省略（今年なら「8/10（月）」）が時の経過で変わらないよう、今日を固定する。
const NOW = new Date('2026-08-01T03:00:00Z');

/** 行の「取り下げる」から確認ダイアログを開く。 */
const openWithdrawDialog = async (index = 0) => {
  fireEvent.click((await screen.findAllByRole('button', { name: '取り下げる' }))[index]);
  return screen.findByRole('alertdialog');
};

/** 行の「取り下げる」を押し、確認ダイアログでも「取り下げる」を選ぶ。 */
const withdrawThroughDialog = async (index = 0) => {
  const dialog = await openWithdrawDialog(index);
  fireEvent.click(within(dialog).getByRole('button', { name: '取り下げる' }));
};

/** 確認ダイアログの外にある、行ごとの「取り下げる」。 */
const rowWithdrawButtons = () =>
  within(screen.getByRole('list', { name: '予約' })).queryAllByRole('button', {
    name: '取り下げる',
  });

const page = (rows: unknown[], number = 0, total = rows.length) => ({
  rows,
  page: number,
  pageCount: Math.ceil(total / PAGE_SIZE),
  total,
});

/** ページ番号で行を返すサーバの代役。 */
const pagedServer = (total: number) => (number: number) => {
  const start = number * PAGE_SIZE;
  const end = Math.min(start + PAGE_SIZE, total);
  const rows = Array.from({ length: end - start }, (_, i) => ({
    id: `o${start + i}`,
    store_name: `店舗${start + i}`,
    business_date: '2026-08-10',
    status: 'PENDING',
    expired: false,
  }));
  return Promise.resolve(page(rows, number, total));
};

describe('MemberReservationsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers({ now: NOW, doNotFake: ['setTimeout', 'setInterval', 'queueMicrotask'] });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('全店舗の予約を店舗名・日時・人数・指名と状態の語で表示する', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'o1',
          store_name: '店舗A',
          business_date: '2026-08-10',
          arrival_scheduled_start_time: '19:00:00',
          pax: 2,
          status: 'PENDING',
          expired: false,
        },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          cast_name: 'さくら',
          status: 'CONFIRMED',
          expired: false,
        },
      ])
    );

    render(<MemberReservationsPage />);

    const rows = within(await screen.findByRole('list', { name: '予約' })).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('店舗A');
    expect(rows[0]).toHaveTextContent('8/10（月）19:00');
    expect(rows[0]).toHaveTextContent('2名');
    expect(rows[0]).toHaveTextContent('指名なし');
    expect(within(rows[0]).getByText('申請中')).toBeInTheDocument();
    expect(rows[1]).toHaveTextContent('店舗B');
    expect(rows[1]).toHaveTextContent('8/11（火）');
    expect(rows[1]).toHaveTextContent('指名: さくら');
    // 店舗が確定した申請は両ポータル共通の語「承認」で出す
    expect(within(rows[1]).getByText('承認')).toBeInTheDocument();
  });

  it('店舗の謝絶は「却下」、本人の取り下げは「取り下げ」、希望日を過ぎた未処理は「失効」と出す', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'o1',
          store_name: '店舗A',
          business_date: '2026-08-10',
          status: 'DECLINED',
          expired: false,
        },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          status: 'WITHDRAWN',
          expired: false,
        },
        // 失効は状態ではなくサーバの導出。画面が日時を比べると営業日の区切りとずれる
        {
          id: 'o3',
          store_name: '店舗C',
          business_date: '2026-07-20',
          status: 'PENDING',
          expired: true,
        },
      ])
    );

    render(<MemberReservationsPage />);

    const rows = within(await screen.findByRole('list', { name: '予約' })).getAllByRole('listitem');
    expect(within(rows[0]).getByText('却下')).toBeInTheDocument();
    expect(within(rows[1]).getByText('取り下げ')).toBeInTheDocument();
    expect(within(rows[2]).getByText('失効')).toBeInTheDocument();
    expect(within(rows[2]).queryByText('申請中')).not.toBeInTheDocument();
    // 店舗がもう処理しない申請に取り下げを出すと、「失効」の隣で何かが残っているように読める
    expect(rowWithdrawButtons()).toHaveLength(0);
  });

  it('一覧の上部から予約の新規作成へ進める', async () => {
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberReservationsPage />);

    expect(await screen.findByRole('link', { name: '新しく予約' })).toHaveAttribute(
      'href',
      '/member/reservations/new'
    );
  });

  it('予約が無いときは無いことと新規作成への入口を一つだけ出す', async () => {
    mockedList.mockResolvedValue(page([]));

    render(<MemberReservationsPage />);

    expect(await screen.findByText('まだ予約はありません')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: '新しく予約' })).toHaveLength(1);
  });

  it('読み込み中は一覧の形の骨組みを出す', () => {
    mockedList.mockReturnValue(new Promise(() => {}));

    render(<MemberReservationsPage />);

    expect(screen.getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
  });

  it('取り下げは確認ダイアログを経て、「やめる」なら取り下げない', async () => {
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberReservationsPage />);

    const dialog = await openWithdrawDialog();
    expect(within(dialog).getByRole('button', { name: '取り下げる' })).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'やめる' }));

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(mockedWithdraw).not.toHaveBeenCalled();
  });

  it('取り下げボタンは確定前の予約にだけ出す', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'o1',
          store_name: '店舗A',
          business_date: '2026-08-10',
          status: 'PENDING',
          expired: false,
        },
        {
          id: 'o2',
          store_name: '店舗B',
          business_date: '2026-08-11',
          status: 'CONFIRMED',
          expired: false,
        },
      ])
    );

    render(<MemberReservationsPage />);

    await screen.findByText('店舗A');
    expect(rowWithdrawButtons()).toHaveLength(1);
  });

  it('取り下げると本人取り下げの API を呼び、表示中のページを取り直す', async () => {
    const pending = {
      id: 'o1',
      store_name: '店舗A',
      business_date: '2026-08-10',
      status: 'PENDING',
      expired: false,
    };
    mockedList
      .mockResolvedValueOnce(page([pending]))
      .mockResolvedValueOnce(page([{ ...pending, status: 'WITHDRAWN' }]));
    mockedWithdraw.mockResolvedValue({ ...pending, status: 'WITHDRAWN' });

    render(<MemberReservationsPage />);

    await withdrawThroughDialog();

    await waitFor(() => expect(mockedWithdraw).toHaveBeenCalledWith('o1'));
    expect(await screen.findByText('取り下げ')).toBeInTheDocument();
    expect(mockedList).toHaveBeenLastCalledWith(0);
    expect(mockedList).toHaveBeenCalledTimes(2);
  });

  it('識別子を欠いた失敗は、汎用文言に潰さずそのまま名乗ること', async () => {
    // 識別子を欠いた要求はアダプタが組む前に止める（order-api.test.ts）。画面が負うのは
    // その失敗を「取り下げに失敗しました」へ潰さないこと
    mockedList.mockResolvedValue(
      page([
        { store_name: '店舗A', business_date: '2026-08-10', status: 'PENDING', expired: false },
      ])
    );
    mockedWithdraw.mockRejectedValue(
      new ClientDataError('予約申請の識別子が取得できていません。画面を読み直してください')
    );

    render(<MemberReservationsPage />);

    await withdrawThroughDialog();

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        expect.stringContaining('予約申請の識別子が取得できていません')
      )
    );
  });

  it('取得に失敗したら領域エラー態を出し、再試行で復帰できる', async () => {
    mockedList.mockRejectedValueOnce(new Error('failed'));
    mockedList.mockImplementation(pagedServer(1));

    render(<MemberReservationsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    expect(screen.queryByText('まだ予約はありません')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
  });

  it('次ページの取得に失敗したら領域ごとエラー態になり、再試行は先頭から取り直す', async () => {
    // 古い行を残すと、読めなかった一覧に前回の内容が居座って「これが最新」に見える。
    const server = pagedServer(25);
    mockedList.mockImplementation(server);

    render(<MemberReservationsPage />);
    expect(await screen.findByText('店舗0')).toBeInTheDocument();

    mockedList.mockImplementation((number: number) =>
      number > 0 ? Promise.reject(new Error('failed')) : server(number)
    );
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);

    expect(await screen.findByRole('alert')).toHaveTextContent('予約を取得できませんでした。');
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('button', { name: '取り下げる' })).toHaveLength(0);

    mockedList.mockImplementation(server);
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith(0));
    await waitFor(() => expect(rowWithdrawButtons()).toHaveLength(20));
  });

  it('ページ送りで次のページを取り直し、最終ページでは先へ進めない', async () => {
    mockedList.mockImplementation(pagedServer(45));

    render(<MemberReservationsPage />);

    expect(await screen.findByText('店舗0')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    expect(await screen.findByText('店舗20')).toBeInTheDocument();
    expect(screen.queryByText('店舗0')).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '次へ' })[0]);
    await waitFor(() => expect(rowWithdrawButtons()).toHaveLength(5));

    expect(mockedList.mock.calls.map(([number]) => number)).toEqual([0, 1, 2]);
    expect(screen.getAllByRole('button', { name: '次へ' })[0]).toBeDisabled();
  });
});
