import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemberHomePage } from '../MemberHomePage';
import { memberApi } from '@/entities/member';
import { memberPointApi } from '@/entities/point';
import { memberOrderApplicationApi } from '@/entities/order';

jest.mock('@/entities/member', () => ({
  memberApi: { home: jest.fn() },
}));
jest.mock('@/entities/point', () => ({
  memberPointApi: { balance: jest.fn() },
}));
jest.mock('@/entities/order', () => ({
  memberOrderApplicationApi: { list: jest.fn() },
}));

const mockedHome = memberApi.home as jest.Mock;
const mockedBalance = memberPointApi.balance as jest.Mock;
const mockedList = memberOrderApplicationApi.list as jest.Mock;

const HOME = { member_code: '123456789012', display_name: '会員花子' };

function page(rows: object[]) {
  return { rows, page: 0, pageCount: 1, total: rows.length };
}

function section(name: string) {
  return screen.getByRole('region', { name });
}

describe('MemberHomePage', () => {
  beforeEach(() => {
    jest.resetAllMocks();
    jest.useFakeTimers({
      now: new Date('2026-11-08T12:00:00+09:00'),
      doNotFake: ['queueMicrotask'],
    });
    mockedHome.mockResolvedValue(HOME);
    mockedBalance.mockResolvedValue({ balance: 1234 });
    mockedList.mockResolvedValue(page([]));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('会員コード・ポイント残高・次の予約をこの順に並べる', async () => {
    render(<MemberHomePage />);

    expect(await screen.findByText('1234 5678 9012')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '会員コードQR' })).toBeInTheDocument();
    expect(screen.getByText('会員花子 さん')).toBeInTheDocument();
    expect(await screen.findByText('1,234')).toBeInTheDocument();

    const headings = screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent);
    expect(headings).toEqual(['会員コード', 'ポイント残高', '次の予約']);
  });

  it('次の予約は期限内の申請中・承認のうち最も近い日を共通の書式で出す', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'far',
          store_name: '遠い店',
          business_date: '2026-12-24',
          status: 'CONFIRMED',
          expired: false,
        },
        {
          id: 'withdrawn',
          store_name: '取り下げた店',
          business_date: '2026-11-09',
          status: 'WITHDRAWN',
          expired: false,
        },
        {
          id: 'near',
          store_name: '近い店',
          business_date: '2026-11-10',
          arrival_scheduled_start_time: '19:30:00',
          status: 'PENDING',
          expired: false,
        },
        {
          id: 'past',
          store_name: '終わった店',
          business_date: '2026-11-07',
          status: 'CONFIRMED',
          expired: false,
        },
        {
          id: 'expired',
          store_name: '失効した店',
          business_date: '2026-11-09',
          status: 'PENDING',
          expired: true,
        },
      ])
    );

    render(<MemberHomePage />);

    const next = section('次の予約');
    const link = await within(next).findByRole('link', { name: /近い店/ });
    expect(link).toHaveAttribute('href', '/member/reservations');
    expect(within(link).getByText('11/10（火）19:30')).toBeInTheDocument();
    expect(within(link).getByText('申請中')).toBeInTheDocument();
    expect(within(next).queryByText('遠い店')).not.toBeInTheDocument();
    expect(within(next).queryByText('失効した店')).not.toBeInTheDocument();
  });

  it('次の予約が無ければ空の表示と「新しく予約」を出す', async () => {
    mockedList.mockResolvedValue(
      page([
        {
          id: 'past',
          store_name: '終わった店',
          business_date: '2026-11-01',
          status: 'CONFIRMED',
          expired: false,
        },
      ])
    );

    render(<MemberHomePage />);

    const next = section('次の予約');
    expect(await within(next).findByText('まだ予約はありません')).toBeInTheDocument();
    // href は Link が末尾の / を落とした形で描かれる（next.config の trailingSlash 既定）。
    expect(within(next).getByRole('link', { name: '新しく予約' })).toHaveAttribute(
      'href',
      '/member/reservations/new'
    );
  });

  it('取得が揃うまでは各区画に読み込み中を出す', () => {
    mockedHome.mockReturnValue(new Promise(() => {}));
    mockedBalance.mockReturnValue(new Promise(() => {}));
    mockedList.mockReturnValue(new Promise(() => {}));

    render(<MemberHomePage />);

    for (const name of ['会員コード', 'ポイント残高', '次の予約']) {
      expect(within(section(name)).getByRole('status', { name: '読み込み中' })).toBeInTheDocument();
    }
  });

  it.each([
    ['会員コード', mockedHome, '会員コードを取得できませんでした', '1234 5678 9012'],
    ['ポイント残高', mockedBalance, 'ポイント残高を取得できませんでした', '1,234'],
    ['次の予約', mockedList, '次の予約を取得できませんでした', 'まだ予約はありません'],
  ])(
    '%s の取得に失敗したらその区画だけが失敗を名乗り、再試行で戻る',
    async (name, mock, message, recovered) => {
      (mock as jest.Mock).mockRejectedValueOnce(new Error('boom'));

      render(<MemberHomePage />);

      const region = await within(section(name)).findByRole('alert');
      expect(within(region).getByText(message)).toBeInTheDocument();
      expect(screen.getAllByRole('alert')).toHaveLength(1);

      fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

      expect(await within(section(name)).findByText(recovered)).toBeInTheDocument();
    }
  );

  it('タブとアカウントへ移った導線をホームに残さない', async () => {
    render(<MemberHomePage />);
    await screen.findByText('1234 5678 9012');

    for (const name of [
      '予約を見る',
      'ポイントを見る',
      '来店履歴を見る',
      '通知の設定',
      '退会する',
    ]) {
      expect(screen.queryByRole('link', { name })).not.toBeInTheDocument();
    }
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument();
  });
});
