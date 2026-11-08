import { render, screen, within } from '@testing-library/react';
import { CastPortalLayout } from '../CastPortalLayout';
import { CastHomePage } from '../CastHomePage';
import { shiftApi } from '@/entities/shift';
import { ownServiceApi } from '@/entities/service';
import { selfMonthlyRemunerationApi } from '@/entities/order';
import { remunerationApi } from '@/widgets/remuneration-statement';

jest.mock('next/navigation', () => ({ usePathname: () => '/cast' }));
jest.mock('@/entities/shift', () => ({
  shiftApi: { mySchedule: jest.fn(), myStores: jest.fn(), myShiftRequests: jest.fn() },
}));
jest.mock('@/entities/service', () => ({ ownServiceApi: { list: jest.fn() } }));
jest.mock('@/entities/order', () => ({ selfMonthlyRemunerationApi: { stores: jest.fn() } }));
jest.mock('@/widgets/remuneration-statement', () => ({
  remunerationApi: { statement: jest.fn() },
}));

const mySchedule = shiftApi.mySchedule as jest.Mock;
const myStores = shiftApi.myStores as jest.Mock;
const myShiftRequests = shiftApi.myShiftRequests as jest.Mock;
const listConditions = ownServiceApi.list as jest.Mock;
const statement = remunerationApi.statement as jest.Mock;
const remunerationStores = selfMonthlyRemunerationApi.stores as jest.Mock;

// 東京 2026-11-08（日）12:00
const NOW = new Date('2026-11-08T03:00:00Z');

const page = <T,>(rows: T[], pageCount = 1, pageIndex = 0) => ({
  rows,
  page: pageIndex,
  pageCount,
  total: rows.length,
});

function day(business_date: string, acceptance_state: string) {
  return { business_date, acceptance_state };
}

// 明細は動きの有無にかかわらず対象月の全日を持つ。動きの無い日は全額 0 で承諾できない。
function quietDays(month: string) {
  const [y, m] = month.split('-').map(Number);
  const count = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return Array.from({ length: count }, (_, i) =>
    day(`${month}-${String(i + 1).padStart(2, '0')}`, 'NOT_ACCEPTABLE')
  );
}

function statementOf(
  storeId: number,
  storeName: string,
  overrides: { month?: string; [key: string]: unknown } = {}
) {
  const month = overrides.month ?? '2026-11';
  return {
    store_id: storeId,
    store_name: storeName,
    month,
    order_total: 0,
    known_guarantee_total: 0,
    bonus_total: 0,
    total: 0,
    days: quietDays(month),
    ...overrides,
  };
}

function renderHome() {
  return render(
    <CastPortalLayout>
      <CastHomePage />
    </CastPortalLayout>
  );
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.useFakeTimers({ now: NOW, doNotFake: ['setTimeout', 'setInterval', 'queueMicrotask'] });
  mySchedule.mockResolvedValue([]);
  myStores.mockResolvedValue([{ store_id: 1, store_name: '店舗A' }]);
  remunerationStores.mockResolvedValue(page([{ store_id: 1, store_name: '店舗A' }]));
  myShiftRequests.mockResolvedValue(page([]));
  listConditions.mockResolvedValue(page([]));
  statement.mockImplementation((scope: { storeId: number }) =>
    Promise.resolve(statementOf(scope.storeId, scope.storeId === 1 ? '店舗A' : '店舗B'))
  );
});

afterEach(() => {
  jest.useRealTimers();
});

describe('CastHomePage', () => {
  it('今日の出勤を店舗名と時間帯で出す', async () => {
    mySchedule.mockResolvedValue([
      {
        id: 's1',
        work_date: '2026-11-08',
        start_time: '19:00:00',
        end_time: '02:00:00',
        store_id: 1,
        store_name: '店舗A',
      },
    ]);

    renderHome();

    const today = await screen.findByRole('region', { name: '今日の出勤' });
    expect(await within(today).findByText('店舗A')).toBeInTheDocument();
    expect(within(today).getByText('19:00〜翌2:00')).toBeInTheDocument();
    expect(mySchedule).toHaveBeenCalledWith({ from: '2026-11-08', to: '2026-11-08' });
  });

  it('受諾待ちのサービス条件を全ページから拾い、店舗名と状態を付けてサービス条件の画面へ導く', async () => {
    myStores.mockResolvedValue([
      { store_id: 1, store_name: '店舗A' },
      { store_id: 2, store_name: '店舗B' },
    ]);
    listConditions.mockImplementation((storeId: string, pageIndex: number) => {
      if (storeId === '1' && pageIndex === 0)
        return Promise.resolve(
          page([{ id: 'c1', name: '受諾済みのコース', consent_status: 'ACCEPTED' }], 2, 0)
        );
      if (storeId === '1')
        return Promise.resolve(
          page([{ id: 'c2', name: '延長', consent_status: 'NOT_ACCEPTED' }], 2, 1)
        );
      return Promise.resolve(
        page([
          { id: 'c3', name: '改定された指名', consent_status: 'RECONFIRMATION_REQUIRED' },
          { id: 'c4', name: '拒否したコース', consent_status: 'REJECTED' },
        ])
      );
    });

    renderHome();

    const todo = await screen.findByRole('list', { name: 'やること' });
    const extension = await within(todo).findByRole('link', { name: /延長/ });
    expect(extension).toHaveAttribute('href', '/cast/service-conditions');
    expect(extension).toHaveTextContent('店舗A');
    expect(extension).toHaveTextContent('受諾待ち');
    expect(within(todo).getByRole('link', { name: /改定された指名/ })).toHaveTextContent('店舗B');
    expect(within(todo).queryByText('受諾済みのコース')).not.toBeInTheDocument();
    expect(within(todo).queryByText('拒否したコース')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ホーム 2件' })).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('今月の承諾待ち・再承諾が必要な日を月次の画面へ導き、件数に数える', async () => {
    statement.mockImplementation((_scope: unknown, month: string) =>
      Promise.resolve(
        month === '2026-11'
          ? statementOf(1, '店舗A', {
              days: [
                day('2026-11-02', 'ACCEPTED'),
                day('2026-11-03', 'NOT_ACCEPTED'),
                day('2026-11-04', 'CHANGED_AFTER_ACCEPTANCE'),
                day('2026-11-08', 'NOT_ACCEPTABLE'),
              ],
            })
          : statementOf(1, '店舗A', { month })
      )
    );

    renderHome();

    const todo = await screen.findByRole('list', { name: 'やること' });
    const waiting = within(todo).getByRole('link', { name: /11\/3（火）/ });
    expect(waiting).toHaveAttribute('href', '/cast/remunerations/monthly?store_id=1&month=2026-11');
    expect(waiting).toHaveTextContent('店舗A');
    expect(waiting).toHaveTextContent('承諾待ち');
    expect(within(todo).getByRole('link', { name: /11\/4（水）/ })).toHaveTextContent(
      '再承諾が必要'
    );
    expect(within(todo).queryByText(/11\/2/)).not.toBeInTheDocument();
    expect(within(todo).queryByText(/11\/8/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ホーム 2件' })).toBeInTheDocument();
    expect(statement).toHaveBeenCalledWith({ scope: 'self', storeId: 1 }, '2026-11', 0, 0);
  });

  it('先月の承諾待ちの日も残して先月の明細へ導き、累計報酬は今月の明細だけで出す', async () => {
    statement.mockImplementation((_scope: unknown, month: string) =>
      Promise.resolve(
        month === '2026-10'
          ? statementOf(1, '店舗A', {
              month,
              total: 99000,
              days: [day('2026-10-31', 'NOT_ACCEPTED')],
            })
          : statementOf(1, '店舗A', { total: 12000 })
      )
    );

    renderHome();

    const todo = await screen.findByRole('list', { name: 'やること' });
    const lastMonth = within(todo).getByRole('link', { name: /10\/31（土）/ });
    expect(lastMonth).toHaveTextContent('承諾待ち');
    expect(lastMonth).toHaveAttribute(
      'href',
      '/cast/remunerations/monthly?store_id=1&month=2026-10'
    );
    expect(screen.getByRole('link', { name: 'ホーム 1件' })).toBeInTheDocument();
    const totals = screen.getByRole('list', { name: '今月の累計報酬' });
    expect(within(totals).getByText('¥12,000')).toBeInTheDocument();
    expect(within(totals).queryByText('¥99,000')).not.toBeInTheDocument();
  });

  it('退店した店舗の承諾待ちの日も残し、今月の動きが無ければ累計には出さない', async () => {
    remunerationStores.mockResolvedValue(
      page([
        { store_id: 1, store_name: '店舗A' },
        { store_id: 2, store_name: '退店した店舗' },
        { store_id: 3, store_name: '昔の店舗' },
      ])
    );
    statement.mockImplementation((scope: { storeId: number }, month: string) => {
      if (scope.storeId === 2)
        return Promise.resolve(
          month === '2026-10'
            ? statementOf(2, '退店した店舗', { month })
            : statementOf(2, '退店した店舗', {
                order_total: 8000,
                total: 8000,
                days: [day('2026-11-01', 'NOT_ACCEPTED'), ...quietDays(month).slice(1)],
              })
        );
      if (scope.storeId === 3) return Promise.resolve(statementOf(3, '昔の店舗', { month }));
      return Promise.resolve(statementOf(1, '店舗A', { month }));
    });

    renderHome();

    const todo = await screen.findByRole('list', { name: 'やること' });
    expect(within(todo).getByRole('link', { name: /11\/1（日）/ })).toHaveTextContent(
      '退店した店舗'
    );
    expect(screen.getByRole('link', { name: 'ホーム 1件' })).toBeInTheDocument();
    expect(listConditions).toHaveBeenCalledTimes(1);
    const totals = screen.getByRole('list', { name: '今月の累計報酬' });
    expect(within(totals).getByText('店舗A')).toBeInTheDocument();
    expect(within(totals).getByText('退店した店舗')).toBeInTheDocument();
    expect(within(totals).queryByText('昔の店舗')).not.toBeInTheDocument();
  });

  it('先月の明細だけが取れないときも今月の累計報酬は出す', async () => {
    statement.mockImplementation((_scope: unknown, month: string) =>
      month === '2026-10'
        ? Promise.reject(new Error('network error'))
        : Promise.resolve(statementOf(1, '店舗A', { total: 12000 }))
    );

    renderHome();

    expect(await screen.findByText('やることの取得に失敗しました')).toBeInTheDocument();
    const totals = await screen.findByRole('list', { name: '今月の累計報酬' });
    expect(within(totals).getByText('¥12,000')).toBeInTheDocument();
  });

  it('1 月は前年の 12 月を先月として読む', async () => {
    jest.setSystemTime(new Date('2027-01-05T03:00:00Z'));

    renderHome();

    await screen.findByRole('list', { name: '今月の累計報酬' });
    expect(statement).toHaveBeenCalledWith({ scope: 'self', storeId: 1 }, '2027-01', 0, 0);
    expect(statement).toHaveBeenCalledWith({ scope: 'self', storeId: 1 }, '2026-12', 0, 0);
  });

  it('今月の累計報酬を店舗ごとに金額の書式で出し、計算できない店舗は計算待ちとする', async () => {
    const stores = [
      { store_id: 1, store_name: '店舗A' },
      { store_id: 2, store_name: '店舗B' },
    ];
    myStores.mockResolvedValue(stores);
    remunerationStores.mockResolvedValue(page(stores));
    statement.mockImplementation((scope: { storeId: number }) =>
      Promise.resolve(
        scope.storeId === 1
          ? statementOf(1, '店舗A', { total: 12000 })
          : statementOf(2, '店舗B', { total: undefined })
      )
    );

    renderHome();

    const totals = await screen.findByRole('list', { name: '今月の累計報酬' });
    const rows = within(totals).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('店舗A');
    expect(rows[0]).toHaveTextContent('¥12,000');
    expect(rows[1]).toHaveTextContent('店舗B');
    expect(rows[1]).toHaveTextContent('計算待ち');
  });

  it('7 日以内に出した申請の結果を希望提出の画面へ導き、件数には数えない', async () => {
    const request = (id: string, overrides: object) => ({
      id,
      work_date: '2026-11-10',
      start_time: '19:00:00',
      end_time: '21:00:00',
      type: 'NEW',
      store_id: 1,
      store_name: '店舗A',
      ...overrides,
    });
    myShiftRequests.mockImplementation((pageIndex: number) =>
      Promise.resolve(
        pageIndex === 0
          ? page(
              [
                request('r1', { status: 'PENDING', created_at: '2026-11-08T01:00:00Z' }),
                request('r2', { status: 'APPROVED', created_at: '2026-11-07T01:00:00Z' }),
                request('r3', {
                  status: 'DECLINED',
                  type: 'CHANGE',
                  work_date: '2026-11-12',
                  created_at: '2026-11-02T01:00:00Z',
                }),
              ],
              2,
              0
            )
          : page(
              [
                request('r4', {
                  status: 'APPROVED',
                  work_date: '2026-10-30',
                  created_at: '2026-10-31T01:00:00Z',
                }),
              ],
              2,
              1
            )
      )
    );

    renderHome();

    const todo = await screen.findByRole('list', { name: 'やること' });
    const approved = within(todo).getByRole('link', { name: /11\/10（火）の出勤希望/ });
    expect(approved).toHaveAttribute('href', '/cast/requests');
    expect(approved).toHaveTextContent('店舗A');
    expect(approved).toHaveTextContent('承認');
    expect(within(todo).getByRole('link', { name: /11\/12（木）の変更申請/ })).toHaveTextContent(
      '却下'
    );
    expect(within(todo).getAllByRole('link')).toHaveLength(2);
    expect(myShiftRequests).toHaveBeenCalledWith(1);
    expect(screen.getByRole('link', { name: 'ホーム' })).toBeInTheDocument();
  });

  it('明細が取れないときはやることを失敗として出し、受諾待ちだけの欠けた件数をタブに出さない', async () => {
    listConditions.mockResolvedValue(
      page([{ id: 'c1', name: '延長', consent_status: 'NOT_ACCEPTED' }])
    );
    statement.mockRejectedValue(new Error('network error'));

    renderHome();

    expect(await screen.findByText('やることの取得に失敗しました')).toBeInTheDocument();
    expect(screen.getByText('今月の報酬の取得に失敗しました')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ホーム' })).toBeInTheDocument();
  });

  it('やることが無いときは空の表示を出し、タブに件数を出さない', async () => {
    renderHome();

    expect(await screen.findByText('いま対応することはありません')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ホーム' })).toHaveAttribute('aria-current', 'page');
  });
});
