import { fireEvent, render, screen, within } from '@testing-library/react';
import { CastRemunerationsPage } from '../CastRemunerationsPage';
import { selfRemunerationApi } from '@/entities/order';
import { tokyoDate } from '@/shared/lib';
import { remunerationApi, type Statement } from '@/widgets/remuneration-statement';

jest.mock('@/entities/order', () => ({
  selfRemunerationApi: { enrollments: jest.fn(), list: jest.fn() },
}));
jest.mock('@/widgets/remuneration-statement', () => ({
  remunerationApi: { statement: jest.fn() },
}));

const ginza = {
  enrollment_id: 'enrollment-ginza',
  store_id: 1,
  store_name: '銀座本店',
  status: 'ENROLLED' as const,
};
const shibuya = {
  enrollment_id: 'enrollment-shibuya',
  store_id: 2,
  store_name: '渋谷店',
  status: 'WITHDRAWN' as const,
  ended_at: '2026-09-30T20:00:00Z',
};
const summary = {
  order_id: 'order-7f3a',
  enrollment_id: 'enrollment-ginza',
  store_id: 1,
  store_name: '銀座本店',
  business_date: '2026-09-14',
  completed_at: '2026-09-15T01:00:00+09:00',
  status: 'COMPLETED' as const,
  completion_invalidated: false,
  agreed_remuneration: 7000,
  planned_remuneration: 0,
  accrued_remuneration: 7000,
};
const page = <T,>(rows: T[], pageCount = 1) => ({ rows, page: 0, pageCount, total: rows.length });
const statement = (over: Partial<Statement>) => ({ ...over }) as Statement;
const thisMonth = tokyoDate(new Date()).slice(0, 7);

beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(selfRemunerationApi.enrollments).mockResolvedValue(page([ginza]));
  jest.mocked(selfRemunerationApi.list).mockResolvedValue(page([summary]));
  jest.mocked(remunerationApi.statement).mockImplementation(async scope =>
    statement({
      order_total: 100000,
      known_guarantee_total: 8000,
      guarantee_total: 8000,
      bonus_total: 20000,
      total: 'storeId' in scope && scope.storeId === 1 ? 128000 : 54000,
    })
  );
});

it('上部に選んだ店舗の今月の累計（受注報酬・保証の不足分・ボーナスの合計）を出す', async () => {
  render(<CastRemunerationsPage />);
  const total = await screen.findByRole('region', { name: '今月の累計' });
  expect(await within(total).findByText('¥128,000')).toBeInTheDocument();
  expect(remunerationApi.statement).toHaveBeenCalledWith(
    { scope: 'self', storeId: 1 },
    thisMonth,
    0,
    0
  );
});

it('保証の計算待ちの日があれば、計算できる分までの合計と断りを出す', async () => {
  jest
    .mocked(remunerationApi.statement)
    .mockResolvedValue(
      statement({ order_total: 100000, known_guarantee_total: 3000, bonus_total: 20000 })
    );
  render(<CastRemunerationsPage />);
  const total = await screen.findByRole('region', { name: '今月の累計' });
  expect(await within(total).findByText('¥123,000')).toBeInTheDocument();
  expect(within(total).getByText(/日額未設定または退勤記録待ちの日があります/)).toBeInTheDocument();
});

it('行に営業日・店舗名・状態・金額を出し、行から詳細の画面へ進める', async () => {
  render(<CastRemunerationsPage />);
  const link = await screen.findByRole('link', { name: /銀座本店/ });
  expect(link).toHaveAttribute('href', '/cast/remunerations/order-7f3a');
  expect(link).toHaveTextContent(/^(2026\/)?9\/14（月）/);
  expect(link).toHaveTextContent('完了');
  expect(link).toHaveTextContent('¥7,000');
  expect(link).not.toHaveTextContent('order-7f3a');
  expect(selfRemunerationApi.list).toHaveBeenCalledWith(0, 'enrollment-ginza');
});

it('これからの受注は予定の報酬、無効化された受注は無効化と出す', async () => {
  jest.mocked(selfRemunerationApi.list).mockResolvedValue(
    page([
      {
        ...summary,
        order_id: 'a',
        status: 'CONFIRMED' as const,
        planned_remuneration: 9000,
        accrued_remuneration: 0,
      },
      { ...summary, order_id: 'b', completion_invalidated: true, accrued_remuneration: 0 },
    ])
  );
  render(<CastRemunerationsPage />);
  const rows = await screen.findAllByRole('link', { name: /銀座本店/ });
  expect(rows[0]).toHaveTextContent('予定');
  expect(rows[0]).toHaveTextContent('¥9,000');
  expect(rows[1]).toHaveTextContent('無効化');
  expect(rows[1]).toHaveTextContent('¥0');
});

it('1 店舗なら店舗のチップを出さない', async () => {
  render(<CastRemunerationsPage />);
  await screen.findByRole('link', { name: /銀座本店/ });
  expect(screen.queryByRole('group', { name: '店舗' })).not.toBeInTheDocument();
});

it('複数店舗なら店舗のチップで一覧と累計を切り替える', async () => {
  jest.mocked(selfRemunerationApi.enrollments).mockResolvedValue(page([ginza, shibuya]));
  render(<CastRemunerationsPage />);
  const chips = await screen.findByRole('group', { name: '店舗' });
  expect(within(chips).getByRole('button', { name: '銀座本店' })).toHaveAttribute(
    'aria-pressed',
    'true'
  );
  await screen.findByText('¥128,000');

  fireEvent.click(within(chips).getByRole('button', { name: '渋谷店' }));
  expect(await screen.findByText('¥54,000')).toBeInTheDocument();
  expect(selfRemunerationApi.list).toHaveBeenLastCalledWith(0, 'enrollment-shibuya');
  expect(remunerationApi.statement).toHaveBeenLastCalledWith(
    { scope: 'self', storeId: 2 },
    thisMonth,
    0,
    0
  );
});

it('同じ店舗に在籍し直したときは在籍の期間でチップを見分ける', async () => {
  const earlier = {
    ...ginza,
    enrollment_id: 'enrollment-ginza-old',
    status: 'WITHDRAWN' as const,
    ended_at: '2025-03-31T03:00:00Z',
  };
  jest.mocked(selfRemunerationApi.enrollments).mockResolvedValue(page([ginza, earlier]));
  render(<CastRemunerationsPage />);
  const chips = await screen.findByRole('group', { name: '店舗' });
  expect(within(chips).getByRole('button', { name: '銀座本店（在籍中）' })).toBeInTheDocument();
  expect(
    within(chips).getByRole('button', { name: '銀座本店（2025/3/31（月）まで）' })
  ).toBeInTheDocument();
});

it('在籍が複数ページにわたっても全店舗をチップに出す', async () => {
  jest
    .mocked(selfRemunerationApi.enrollments)
    .mockResolvedValueOnce(page([ginza], 2))
    .mockResolvedValueOnce({ rows: [shibuya], page: 1, pageCount: 2, total: 2 });
  render(<CastRemunerationsPage />);
  const chips = await screen.findByRole('group', { name: '店舗' });
  expect(within(chips).getByRole('button', { name: '渋谷店' })).toBeInTheDocument();
  expect(selfRemunerationApi.enrollments).toHaveBeenLastCalledWith(1);
});

it('明細が無ければ空であることを出し、失敗とは扱わない', async () => {
  jest.mocked(selfRemunerationApi.list).mockResolvedValue(page([]));
  render(<CastRemunerationsPage />);
  expect(await screen.findByText('まだ報酬明細はありません')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

it('在籍が無ければ空であることを出し、一覧も累計も取りに行かない', async () => {
  jest.mocked(selfRemunerationApi.enrollments).mockResolvedValue(page([]));
  render(<CastRemunerationsPage />);
  expect(await screen.findByText('まだ報酬明細はありません')).toBeInTheDocument();
  expect(selfRemunerationApi.list).not.toHaveBeenCalled();
  expect(remunerationApi.statement).not.toHaveBeenCalled();
});

it('一覧の失敗はその場で再試行でき、空とは区別する', async () => {
  jest.mocked(selfRemunerationApi.list).mockRejectedValueOnce(new Error('network'));
  render(<CastRemunerationsPage />);
  await screen.findByText('報酬明細の取得に失敗しました');
  expect(screen.queryByText('まだ報酬明細はありません')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /再試行/ }));
  expect(await screen.findByRole('link', { name: /銀座本店/ })).toBeInTheDocument();
});

it('累計の失敗は累計の区画だけに出し、一覧は出す', async () => {
  jest.mocked(remunerationApi.statement).mockRejectedValueOnce(new Error('network'));
  render(<CastRemunerationsPage />);
  const total = await screen.findByRole('region', { name: '今月の累計' });
  fireEvent.click(await within(total).findByRole('button', { name: /再試行/ }));
  expect(await within(total).findByText('¥128,000')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /銀座本店/ })).toBeInTheDocument();
});

it('在籍を見る権限が無ければその旨を出す', async () => {
  jest.mocked(selfRemunerationApi.enrollments).mockRejectedValue({ response: { status: 403 } });
  render(<CastRemunerationsPage />);
  expect(await screen.findByText('報酬明細を確認する権限がありません。')).toBeInTheDocument();
});

it('在籍の取得の失敗はその場で再試行できる', async () => {
  jest.mocked(selfRemunerationApi.enrollments).mockRejectedValueOnce(new Error('network'));
  render(<CastRemunerationsPage />);
  fireEvent.click(await screen.findByRole('button', { name: /再試行/ }));
  expect(await screen.findByRole('link', { name: /銀座本店/ })).toBeInTheDocument();
});
