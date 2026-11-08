import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { CastMonthlyRemunerationsPage } from '../CastMonthlyRemunerationsPage';
import { selfMonthlyRemunerationApi } from '@/entities/order';
import { formatDate, formatDateTime, tokyoDate } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { remunerationApi, type Day, type Statement } from '@/widgets/remuneration-statement';

jest.mock('@/entities/order', () => ({
  selfMonthlyRemunerationApi: { stores: jest.fn() },
}));
jest.mock('@/widgets/remuneration-statement', () => ({
  remunerationApi: { statement: jest.fn(), accept: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
}));
// URL の正本は jsdom の履歴。Next は history.pushState を取り込んで useSearchParams を後の描画で
// 合わせるので、代役も通知を遅らせる（URL と描画済みの条件がずれる間の操作を再現するため）
const MONTHLY_PATH = '/cast/remunerations/monthly';
const mockQueryListeners = new Set<() => void>();
const notifyQuery = () => mockQueryListeners.forEach(listener => listener());
const originalPushState = window.history.pushState.bind(window.history);
window.history.pushState = (...args: Parameters<History['pushState']>) => {
  originalPushState(...args);
  setTimeout(notifyQuery, 0);
};
/** 直接開く・戻るの代役。URL を差し替えて購読中の描画へ知らせる。 */
const visit = (href: string) => {
  window.history.replaceState(null, '', href);
  notifyQuery();
};
const currentQuery = () => new URLSearchParams(window.location.search);

jest.mock('next/navigation', () => {
  const React = jest.requireActual('react');
  return {
    useSearchParams: () => {
      const query = React.useSyncExternalStore(
        (listener: () => void) => {
          mockQueryListeners.add(listener);
          return () => mockQueryListeners.delete(listener);
        },
        () => window.location.search.slice(1)
      );
      return React.useMemo(() => new URLSearchParams(query), [query]);
    },
    usePathname: () => MONTHLY_PATH,
  };
});
jest.mock('@/features/monthly-remuneration-pdf', () => ({
  MonthlyPdfActions: ({ criteria }: { criteria: unknown }) => (
    <p data-testid="pdf">{JSON.stringify(criteria)}</p>
  ),
}));

const today = tokyoDate(new Date());
const thisMonth = today.slice(0, 7);
const previousMonth = (() => {
  const [y, m] = thisMonth.split('-').map(Number);
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`;
})();
const date = (d: number) => `${thisMonth}-${String(d).padStart(2, '0')}`;
const label = (d: number) => formatDate(date(d), today);

const day = (d: number, over: Partial<Day> = {}): Day => ({
  business_date: date(d),
  order_amount: 0,
  closed_duration: 'PT0S',
  attendance_incomplete: false,
  guarantee_status: 'NOT_ELIGIBLE',
  guarantee_amount: 0,
  bonus_amount: 0,
  acceptance_state: 'NOT_ACCEPTABLE',
  ...over,
});
const order = (d: number, id: string, amount: number, name = `${id} のコース`) => ({
  order_id: id,
  business_date: date(d),
  service_summary: name,
  accrued_remuneration: amount,
  completion_invalidated: false,
});
const page = <T,>(content: T[], total_pages = 1) => ({
  content,
  number: 0,
  size: 100,
  total_elements: content.length,
  total_pages,
});

const longCourse = 'ロングコース 120分＋延長30分（指名・オプション込み）';
function statement(over: Partial<Statement> = {}): Statement {
  return {
    store_id: 1,
    store_name: '銀座本店',
    person_id: 9,
    name: '本人',
    month: thisMonth,
    generated_at: `${thisMonth}-20T01:00:00Z`,
    order_total: 36000,
    known_guarantee_total: 2000,
    guarantee_total: 2000,
    bonus_total: 3000,
    total: 41000,
    accepted_days: 1,
    acceptable_days: 3,
    days: [
      day(1),
      day(2, {
        closed_duration: 'PT4H30M',
        order_amount: 13000,
        guarantee_status: 'CALCULATED',
        daily_amount: 15000,
        guarantee_amount: 2000,
        acceptance_state: 'ACCEPTED',
        latest_acceptance: {
          id: 'acc-2',
          accepted_at: `${date(3)}T01:12:00Z`,
          order_amount: 13000,
          guarantee_amount: 2000,
          bonus_amount: 0,
        },
      }),
      day(3, {
        closed_duration: 'PT5H',
        order_amount: 21000,
        bonus_amount: 3000,
        acceptance_state: 'NOT_ACCEPTED',
        content_digest: 'digest-3',
      }),
      day(4, {
        closed_duration: 'PT2H',
        order_amount: 2000,
        acceptance_state: 'CHANGED_AFTER_ACCEPTANCE',
        content_digest: 'digest-4',
        latest_acceptance: {
          id: 'acc-4',
          accepted_at: `${date(5)}T01:00:00Z`,
          order_amount: 8000,
          guarantee_amount: 0,
          bonus_amount: 0,
        },
      }),
      day(5, {
        closed_duration: 'PT3H',
        guarantee_status: 'PENDING_ATTENDANCE',
        guarantee_amount: undefined,
        attendance_incomplete: true,
        acceptance_state: 'NOT_ACCEPTABLE',
        not_acceptable_reason: 'GUARANTEE_UNDETERMINED',
      }),
    ],
    orders: page([
      order(4, 'order-4', 2000),
      order(3, 'order-3', 21000, longCourse),
      order(2, 'order-2b', 5000),
      order(2, 'order-2a', 8000),
    ]),
    bonus_awards: page([
      {
        id: 'bonus-3',
        award_date: date(3),
        effective_amount: 3000,
        reason: '指名ボーナス',
        cancelled: false,
      },
    ]),
    ...over,
  };
}

const dayHeaders = () =>
  screen.getAllByRole('button').filter(button => button.hasAttribute('aria-expanded'));
const dayButton = (d: number) => {
  const found = dayHeaders().find(button => button.textContent?.startsWith(label(d)));
  if (!found) throw new Error(`${label(d)} の行がありません`);
  return found;
};
const hasDay = (d: number) => dayHeaders().some(b => b.textContent?.startsWith(label(d)));
const dayRegion = (d: number) => screen.getByRole('region', { name: `${label(d)}の明細` });

beforeEach(() => {
  jest.resetAllMocks();
  visit(MONTHLY_PATH);
  jest.mocked(selfMonthlyRemunerationApi.stores).mockResolvedValue({
    rows: [{ store_id: 1, store_name: '銀座本店' }],
    page: 0,
    pageCount: 1,
    total: 1,
  });
  jest.mocked(remunerationApi.statement).mockResolvedValue(statement());
});

async function renderPage() {
  render(<CastMonthlyRemunerationsPage />);
  await screen.findByRole('region', { name: /月の合計/ });
}

it('今月の合計（受注報酬・日額保証の不足分・ボーナス・合計・承諾の日数）と PDF を表なしで出す', async () => {
  await renderPage();
  expect(remunerationApi.statement).toHaveBeenCalledWith(
    { scope: 'self', storeId: 1 },
    thisMonth,
    0,
    0,
    100
  );
  const summary = screen.getByRole('region', { name: /月の合計/ });
  expect(within(summary).getByText('¥41,000')).toBeInTheDocument();
  expect(within(summary).getByText('¥36,000')).toBeInTheDocument();
  expect(within(summary).getByText('¥2,000')).toBeInTheDocument();
  expect(within(summary).getByText('¥3,000')).toBeInTheDocument();
  expect(within(summary).getByText('承諾 1 / 3 日')).toBeInTheDocument();
  expect(within(summary).getByTestId('pdf')).toHaveTextContent(
    JSON.stringify({ scope: 'self', storeId: 1, month: thisMonth })
  );
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

it('保証が未確定の日があれば合計を出さずに計算待ちと出す', async () => {
  jest
    .mocked(remunerationApi.statement)
    .mockResolvedValue(statement({ total: undefined, guarantee_total: undefined }));
  await renderPage();
  const summary = screen.getByRole('region', { name: /月の合計/ });
  expect(within(summary).getAllByText('計算待ち')).toHaveLength(2);
  expect(within(summary).queryByText('¥41,000')).not.toBeInTheDocument();
});

it('出勤などの無い日を出さず、承諾待ち・再承諾が必要な日を先頭にして最初から開く', async () => {
  await renderPage();
  expect(dayHeaders().map(button => button.textContent)).toEqual([
    expect.stringContaining(label(3)),
    expect.stringContaining(label(4)),
    expect.stringContaining(label(2)),
    expect.stringContaining(label(5)),
  ]);
  expect(hasDay(1)).toBe(false);
  expect(dayButton(3)).toHaveAttribute('aria-expanded', 'true');
  expect(dayButton(4)).toHaveAttribute('aria-expanded', 'true');
  expect(dayButton(2)).toHaveAttribute('aria-expanded', 'false');
  expect(dayButton(3)).toHaveTextContent('出勤 5時間 · 受注1件 · ボーナスあり');
  expect(dayButton(3)).toHaveTextContent('¥24,000');
  expect(dayButton(3)).toHaveTextContent('承諾待ち');
  expect(dayButton(4)).toHaveTextContent('再承諾が必要');
  expect(dayButton(2)).toHaveTextContent('承諾済み');
});

it('開いた日に受注（番号は出さない）・ボーナス・その日の合計と、日付を名乗る承諾のボタンを 1 個だけ出す', async () => {
  await renderPage();
  const third = dayRegion(3);
  expect(
    within(third).getByRole('link', { name: name => name.includes(longCourse) })
  ).toHaveAttribute('href', '/cast/remunerations/order-3');
  expect(within(third).getByText('指名ボーナス')).toBeInTheDocument();
  expect(within(third).getByText('この日の合計')).toBeInTheDocument();
  expect(screen.queryByText(/order-3/)).not.toBeInTheDocument();
  expect(within(third).getAllByRole('button')).toHaveLength(1);
  expect(within(third).getByRole('button', { name: `${label(3)}を承諾する` })).toBeInTheDocument();
});

it('閉じた日を開くと、出勤・受注・日額保証の不足分と承諾した日時が出る', async () => {
  await renderPage();
  fireEvent.click(dayButton(2));
  const second = dayRegion(2);
  expect(within(second).getByText('4時間30分')).toBeInTheDocument();
  expect(within(second).getByRole('link', { name: /order-2a のコース/ })).toBeInTheDocument();
  expect(within(second).getByRole('link', { name: /order-2b のコース/ })).toBeInTheDocument();
  expect(within(second).getByText('日額 ¥15,000 に対する不足分')).toBeInTheDocument();
  expect(
    within(second).getByText(`${formatDateTime(`${date(3)}T01:12:00Z`, today)}に承諾しました`)
  ).toBeInTheDocument();
  expect(within(second).queryByRole('button')).toBeNull();
  fireEvent.click(dayButton(2));
  expect(dayButton(2)).toHaveAttribute('aria-expanded', 'false');
});

it('承諾後に変わった日は変更前後の差と再承諾のボタンを出す', async () => {
  await renderPage();
  const fourth = dayRegion(4);
  expect(within(fourth).getByText('受注報酬')).toBeInTheDocument();
  expect(within(fourth).getByText('¥8,000 → ¥2,000')).toBeInTheDocument();
  expect(
    within(fourth).getByRole('button', { name: `${label(4)}を再承諾する` })
  ).toBeInTheDocument();
});

it('承諾できない日は理由を出し、ボタンを出さない', async () => {
  await renderPage();
  fireEvent.click(dayButton(5));
  const fifth = dayRegion(5);
  expect(within(fifth).getByText('計算待ち（退勤記録待ち）')).toBeInTheDocument();
  expect(within(fifth).getByText('退勤の記録と日額が確定すると承諾できます')).toBeInTheDocument();
  expect(within(fifth).queryByRole('button')).toBeNull();
});

it('不足分が 0 円の日も、承諾の根拠になる日額と保証の停止を出す', async () => {
  const base = statement();
  jest.mocked(remunerationApi.statement).mockResolvedValue(
    statement({
      days: [
        ...base.days,
        day(6, {
          closed_duration: 'PT6H',
          order_amount: 30000,
          guarantee_status: 'CALCULATED',
          daily_amount: 25000,
          acceptance_state: 'ACCEPTED',
        }),
        day(7, {
          closed_duration: 'PT6H',
          order_amount: 30000,
          guarantee_status: 'STOPPED',
          guarantee_state: 'STOPPED',
          daily_amount: 25000,
          acceptance_state: 'ACCEPTED',
        }),
      ],
      orders: page([
        ...base.orders.content,
        order(6, 'order-6', 30000),
        order(7, 'order-7', 30000),
      ]),
    })
  );
  await renderPage();
  fireEvent.click(dayButton(6));
  expect(within(dayRegion(6)).getByText('日額 ¥25,000 に対する不足分')).toBeInTheDocument();
  fireEvent.click(dayButton(7));
  expect(within(dayRegion(7)).getByText('保証停止')).toBeInTheDocument();
});

it('承諾した後に承諾できなくなった日・再承諾が必要な日にも、前に承諾した日時を出す', async () => {
  const base = statement();
  jest.mocked(remunerationApi.statement).mockResolvedValue(
    statement({
      days: [
        ...base.days,
        day(6, {
          closed_duration: 'PT1H',
          not_acceptable_reason: 'ALL_ZERO',
          latest_acceptance: {
            id: 'acc-6',
            accepted_at: `${date(7)}T02:00:00Z`,
            order_amount: 6000,
            guarantee_amount: 0,
            bonus_amount: 0,
          },
        }),
      ],
    })
  );
  await renderPage();
  fireEvent.click(dayButton(6));
  const sixth = dayRegion(6);
  expect(within(sixth).getByText('今は報酬が無いため承諾できません')).toBeInTheDocument();
  expect(
    within(sixth).getByText(`${formatDateTime(`${date(7)}T02:00:00Z`, today)}に承諾しました`)
  ).toBeInTheDocument();
  expect(
    within(dayRegion(4)).getByText(`${formatDateTime(`${date(5)}T01:00:00Z`, today)}に承諾しました`)
  ).toBeInTheDocument();
});

it('承諾すると内容の要約を送り、明細を取り直す。開いていた日は開いたままにする', async () => {
  jest.mocked(remunerationApi.accept).mockResolvedValue({
    id: 'acc',
    accepted_at: `${date(20)}T01:00:00Z`,
    order_amount: 21000,
    guarantee_amount: 0,
    bonus_amount: 3000,
  });
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: `${label(3)}を承諾する` }));
  await waitFor(() => expect(remunerationApi.statement).toHaveBeenCalledTimes(2));
  expect(remunerationApi.accept).toHaveBeenCalledWith({
    store_id: 1,
    business_date: date(3),
    content_digest: 'digest-3',
  });
  expect(notify.success).toHaveBeenCalledWith(`${label(3)}の明細を承諾しました`);
  expect(dayButton(3)).toHaveAttribute('aria-expanded', 'true');
});

it('承諾の後、明細を取り直し終えるまで承諾のボタンを押せないままにする', async () => {
  jest.mocked(remunerationApi.accept).mockResolvedValue({
    id: 'acc',
    accepted_at: `${date(20)}T01:00:00Z`,
    order_amount: 21000,
    guarantee_amount: 0,
    bonus_amount: 3000,
  });
  let finish!: (value: Statement) => void;
  await renderPage();
  jest
    .mocked(remunerationApi.statement)
    .mockImplementationOnce(() => new Promise<Statement>(resolve => (finish = resolve)));
  fireEvent.click(screen.getByRole('button', { name: `${label(3)}を承諾する` }));
  await waitFor(() => expect(remunerationApi.statement).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('button', { name: `${label(3)}を承諾する` })).toBeDisabled();
  await act(async () => finish(statement()));
  expect(screen.getByRole('button', { name: `${label(3)}を承諾する` })).toBeEnabled();
});

it('承諾が 409 なら最新の明細を取り直して確かめるよう促す', async () => {
  jest.mocked(remunerationApi.accept).mockRejectedValue({ response: { status: 409 } });
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: `${label(4)}を再承諾する` }));
  await waitFor(() => expect(remunerationApi.statement).toHaveBeenCalledTimes(2));
  expect(notify.warning).toHaveBeenCalled();
});

it('「承諾待ち」で承諾待ち・再承諾が必要な日だけに絞る', async () => {
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: '承諾待ち 2' }));
  expect(hasDay(2)).toBe(false);
  expect(hasDay(3)).toBe(true);
  expect(hasDay(4)).toBe(true);
});

it('受注とボーナスのページを最後まで読み、各日へ割り振る', async () => {
  const twoPages = <T,>(content: T[]) => ({ ...page(content, 2), total_elements: 4 });
  jest.mocked(remunerationApi.statement).mockImplementation(async (_scope, _month, orderPage) =>
    orderPage === 0
      ? statement({
          orders: twoPages([
            order(4, 'order-4', 2000),
            order(3, 'order-3', 21000),
            order(2, 'order-2b', 5000),
          ]),
        })
      : statement({ orders: twoPages([order(2, 'order-2a', 8000)]), bonus_awards: page([]) })
  );
  await renderPage();
  expect(remunerationApi.statement).toHaveBeenCalledWith(
    { scope: 'self', storeId: 1 },
    thisMonth,
    1,
    1,
    100
  );
  fireEvent.click(dayButton(2));
  expect(within(dayRegion(2)).getByRole('link', { name: /order-2a のコース/ })).toBeInTheDocument();
});

it('ページの間で受注が変わって根拠が合わなければ、全体を取り直す', async () => {
  const first = statement({
    orders: { ...page([order(3, 'order-3', 21000)], 2), total_elements: 2 },
    days: [day(3, { order_amount: 21000, acceptance_state: 'NOT_ACCEPTED', content_digest: 'd' })],
    bonus_awards: page([]),
  });
  jest
    .mocked(remunerationApi.statement)
    // 2 ページ目の取得の前に同じ日の受注が先頭に増え、1 ページ目の行が繰り下がって重複した
    .mockResolvedValueOnce(first)
    .mockResolvedValueOnce({
      ...first,
      orders: { ...page([order(3, 'order-3', 21000)], 2), total_elements: 2 },
    })
    .mockResolvedValue({
      ...first,
      orders: page([order(3, 'order-3', 21000)]),
    });
  await renderPage();
  expect(remunerationApi.statement).toHaveBeenCalledTimes(3);
  expect(within(dayRegion(3)).getAllByRole('link')).toHaveLength(1);
});

it('読み直しても根拠が合わないままなら、合わない行を出さずに再試行を出す', async () => {
  jest
    .mocked(remunerationApi.statement)
    .mockResolvedValue(statement({ orders: page([order(3, 'order-3', 1)]) }));
  render(<CastMonthlyRemunerationsPage />);
  expect(await screen.findByText('月次明細の取得に失敗しました')).toBeInTheDocument();
  expect(remunerationApi.statement).toHaveBeenCalledTimes(3);
  expect(screen.queryByRole('region', { name: /月の合計/ })).toBeNull();
});

it('PDF が受注報酬だけの集計であることを PDF の近くに出す', async () => {
  await renderPage();
  const summary = screen.getByRole('region', { name: /月の合計/ });
  expect(within(summary).getByText(/PDF は受注報酬だけの集計です/)).toBeInTheDocument();
});

it('月を ‹ で前の月へ切り替えると、その月の明細と PDF に替える', async () => {
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: '前の月' }));
  await waitFor(() =>
    expect(remunerationApi.statement).toHaveBeenLastCalledWith(
      { scope: 'self', storeId: 1 },
      previousMonth,
      0,
      0,
      100
    )
  );
  await waitFor(() =>
    expect(screen.getByTestId('pdf')).toHaveTextContent(
      JSON.stringify({ scope: 'self', storeId: 1, month: previousMonth })
    )
  );
  expect(currentQuery().get('month')).toBe(previousMonth);
});

it('複数の店舗の明細があればチップで店舗を切り替える', async () => {
  jest.mocked(selfMonthlyRemunerationApi.stores).mockResolvedValue({
    rows: [
      { store_id: 1, store_name: '銀座本店' },
      { store_id: 2, store_name: '新宿東口店' },
    ],
    page: 0,
    pageCount: 1,
    total: 2,
  });
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: '新宿東口店' }));
  await waitFor(() =>
    expect(remunerationApi.statement).toHaveBeenLastCalledWith(
      { scope: 'self', storeId: 2 },
      thisMonth,
      0,
      0,
      100
    )
  );
  expect(currentQuery().get('store_id')).toBe('2');
});

const twoStores = () =>
  jest.mocked(selfMonthlyRemunerationApi.stores).mockResolvedValue({
    rows: [
      { store_id: 1, store_name: '銀座本店' },
      { store_id: 2, store_name: '新宿東口店' },
    ],
    page: 0,
    pageCount: 1,
    total: 2,
  });

it('URL の店舗と月で開く（詳細から戻ったときや「やること」から開いたとき）', async () => {
  twoStores();
  visit(`${MONTHLY_PATH}?store_id=2&month=${previousMonth}`);
  await renderPage();
  expect(remunerationApi.statement).toHaveBeenCalledWith(
    { scope: 'self', storeId: 2 },
    previousMonth,
    0,
    0,
    100
  );
  expect(remunerationApi.statement).not.toHaveBeenCalledWith(
    expect.anything(),
    thisMonth,
    expect.anything(),
    expect.anything(),
    expect.anything()
  );
  expect(screen.getByRole('button', { name: '新宿東口店' })).toHaveAttribute(
    'aria-pressed',
    'true'
  );
});

it.each([
  ['書式違いの月', 'store_id=1&month=2026-1'],
  ['12 か月より前の月', 'store_id=1&month=2000-01'],
  ['来月より先の月', 'store_id=1&month=9999-12'],
  ['本人に無い店舗', 'store_id=99&month='],
])('%s の URL は今月・最初の店舗で開く', async (_case, query) => {
  twoStores();
  visit(`${MONTHLY_PATH}?${query}`);
  await renderPage();
  expect(remunerationApi.statement).toHaveBeenCalledTimes(1);
  expect(remunerationApi.statement).toHaveBeenCalledWith(
    { scope: 'self', storeId: 1 },
    thisMonth,
    0,
    0,
    100
  );
});

it('描画が URL に追いつく前に店舗と月を続けて切り替えても、両方を URL に残す', async () => {
  twoStores();
  await renderPage();
  fireEvent.click(screen.getByRole('button', { name: '新宿東口店' }));
  fireEvent.click(screen.getByRole('button', { name: '前の月' }));
  expect(currentQuery().get('store_id')).toBe('2');
  expect(currentQuery().get('month')).toBe(previousMonth);
  await waitFor(() =>
    expect(remunerationApi.statement).toHaveBeenLastCalledWith(
      { scope: 'self', storeId: 2 },
      previousMonth,
      0,
      0,
      100
    )
  );
});

it('出勤の無い月は何が無いかを出す', async () => {
  jest
    .mocked(remunerationApi.statement)
    .mockResolvedValue(statement({ days: [day(1)], orders: page([]), bonus_awards: page([]) }));
  await renderPage();
  expect(screen.getByText('この月の出勤はありません')).toBeInTheDocument();
});

it('明細の取得に失敗したら、その区画で再試行できる', async () => {
  jest.mocked(remunerationApi.statement).mockRejectedValueOnce(new Error('network'));
  render(<CastMonthlyRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: /再試行/ }));
  await screen.findByRole('region', { name: /月の合計/ });
});

it('店舗の取得に失敗したら再試行でき、明細の無い本人には何が無いかを出す', async () => {
  jest
    .mocked(selfMonthlyRemunerationApi.stores)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ rows: [], page: 0, pageCount: 0, total: 0 });
  render(<CastMonthlyRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: /再試行/ }));
  expect(await screen.findByText('まだ報酬明細はありません')).toBeInTheDocument();
  expect(remunerationApi.statement).not.toHaveBeenCalled();
});
