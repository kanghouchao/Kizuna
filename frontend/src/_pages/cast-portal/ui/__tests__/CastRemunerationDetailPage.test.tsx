import { fireEvent, render, screen, within } from '@testing-library/react';
import { CastRemunerationDetailPage } from '../CastRemunerationDetailPage';
import { selfRemunerationApi, type SelfRemunerationChange } from '@/entities/order';

jest.mock('next/navigation', () => ({ useParams: () => ({ orderId: 'order-7f3a' }) }));
jest.mock('@/entities/order', () => ({
  selfRemunerationApi: { detail: jest.fn(), changes: jest.fn() },
}));

const item = {
  kind: 'COURSE' as const,
  name: 'とても長いコース名が入るプレミアムリラクゼーション九十分コース',
  price: 12000,
  remuneration: 7000,
  line_id: 'line-5c1e',
  duration_minutes: 90,
  service_id: 'service-9d2b',
  revision_id: 'revision-1a2b',
  revision_number: 2,
  adoption_basis: 'ACCEPTED_TERMS' as const,
  adopted_at: '2026-09-14T10:00:00Z',
  consent_event_id: 'consent-4e8f',
  consent_version: 3,
};
const detail = {
  order_id: 'order-7f3a',
  enrollment_id: 'enrollment-2c9d',
  store_id: 1,
  store_name: '銀座本店',
  business_date: '2026-09-14',
  completed_at: '2026-09-15T01:00:00+09:00',
  status: 'COMPLETED' as const,
  completion_invalidated: true,
  agreed_remuneration: 7000,
  planned_remuneration: 0,
  accrued_remuneration: 0,
  version: 2,
  items: [item],
};
const invalidation: SelfRemunerationChange = {
  change_id: 'change-6b7c',
  change_type: 'COMPLETION_INVALIDATION',
  order_id: 'order-7f3a',
  business_date: '2026-09-14',
  completed_at: '2026-09-15T01:00:00+09:00',
  changed_at: '2026-09-16T10:00:00Z',
  reason: '未提供のため無効化',
  before_version: 1,
  after_version: 2,
  before: {
    items: [item],
    agreed_remuneration: 7000,
    accrued_remuneration: 7000,
    completion_invalidated: false,
  },
  after: {
    items: [item],
    agreed_remuneration: 7000,
    accrued_remuneration: 0,
    completion_invalidated: true,
  },
};
const page = <T,>(rows: T[], pageCount = 1, at = 0) => ({
  rows,
  page: at,
  pageCount,
  total: rows.length,
});

beforeEach(() => {
  jest.resetAllMocks();
  jest.mocked(selfRemunerationApi.detail).mockResolvedValue(detail);
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue(page([invalidation]));
});

/** ラベルの dt に対応する dd の文字。 */
function valueOf(region: HTMLElement, label: string) {
  const term = [...region.querySelectorAll('dt')].find(dt => dt.textContent === label);
  if (!term) throw new Error(`ラベル「${label}」が見つからない`);
  return term.nextElementSibling?.textContent;
}

it('受注の概要と内訳をラベル付きで出し、日付・金額・時間を共通の書式で出す', async () => {
  render(<CastRemunerationDetailPage />);
  const summary = await screen.findByRole('region', { name: '概要' });
  expect(selfRemunerationApi.detail).toHaveBeenCalledWith('order-7f3a');
  expect(valueOf(summary, '店舗')).toBe('銀座本店');
  expect(valueOf(summary, '営業日')).toMatch(/^(2026\/)?9\/14（月）$/);
  expect(valueOf(summary, '完了')).toMatch(/^(2026\/)?9\/15（火）1:00$/);
  expect(valueOf(summary, '状態')).toBe('無効化');
  expect(valueOf(summary, '約定報酬')).toBe('¥7,000');
  expect(valueOf(summary, '発生済み報酬')).toBe('¥0');

  const course = screen.getByRole('region', { name: item.name });
  expect(valueOf(course, '種類')).toBe('コース');
  expect(valueOf(course, '時間')).toBe('90分');
  expect(valueOf(course, 'お客様料金')).toBe('¥12,000');
  expect(valueOf(course, '固定報酬')).toBe('¥7,000');
  expect(valueOf(course, '条件')).toBe('受諾した条件（第3版）');
});

it('変更履歴を変更前後の差で出す', async () => {
  render(<CastRemunerationDetailPage />);
  const history = await screen.findByRole('region', { name: '変更履歴' });
  const change = await within(history).findByRole('article', { name: /無効化/ });
  expect(within(change).getByText(/^(2026\/)?9\/16（水）19:00$/)).toBeInTheDocument();
  expect(valueOf(change, '理由')).toBe('未提供のため無効化');
  expect(valueOf(change, '状態')).toBe('有効 → 無効化');
  expect(valueOf(change, '発生済み報酬')).toBe('¥7,000 → ¥0');
  expect([...change.querySelectorAll('dt')].map(dt => dt.textContent)).not.toContain('約定報酬');
});

it('内部 ID と ISO 形式の日時を画面に出さない', async () => {
  const { container } = render(<CastRemunerationDetailPage />);
  await screen.findByRole('article', { name: /無効化/ });
  const text = container.textContent ?? '';
  for (const id of [
    'order-7f3a',
    'enrollment-2c9d',
    'line-5c1e',
    'service-9d2b',
    'revision-1a2b',
    'consent-4e8f',
    'change-6b7c',
  ])
    expect(text).not.toContain(id);
  expect(text).not.toMatch(/\d{4}-\d{2}-\d{2}/);
  expect(text).not.toMatch(/PT\d/);
});

it('変更が無ければ空であることを出す', async () => {
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue(page([]));
  render(<CastRemunerationDetailPage />);
  expect(await screen.findByText('変更履歴はありません')).toBeInTheDocument();
});

it.each([
  [403, '報酬明細を確認する権限がありません。'],
  [404, '対象の在籍または報酬明細が見つかりません。'],
])('明細の取得が %s なら理由を出し、内容を出さない', async (status, message) => {
  jest.mocked(selfRemunerationApi.detail).mockRejectedValue({ response: { status } });
  render(<CastRemunerationDetailPage />);
  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.queryByText(item.name)).not.toBeInTheDocument();
});

it('明細の取得の失敗はその場で再試行できる', async () => {
  jest.mocked(selfRemunerationApi.detail).mockRejectedValueOnce(new Error('network'));
  render(<CastRemunerationDetailPage />);
  fireEvent.click(await screen.findByRole('button', { name: /再試行/ }));
  expect(await screen.findByRole('region', { name: '概要' })).toBeInTheDocument();
});

it('変更履歴の失敗を再試行し、ページを送れる', async () => {
  jest
    .mocked(selfRemunerationApi.changes)
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce(page([invalidation], 2))
    .mockResolvedValueOnce(page([], 2, 1));
  render(<CastRemunerationDetailPage />);
  const history = await screen.findByRole('region', { name: '変更履歴' });
  fireEvent.click(await within(history).findByRole('button', { name: /再試行/ }));
  fireEvent.click((await within(history).findAllByRole('button', { name: '次へ' }))[0]);
  expect(
    await within(history).findByRole('button', { name: '2', current: 'page' })
  ).toBeInTheDocument();
  expect(selfRemunerationApi.changes).toHaveBeenLastCalledWith('order-7f3a', 1);
});

it('訂正で増えた明細は明細名に（追加）を添え、その固定報酬を出す', async () => {
  const extension = {
    kind: 'EXTENSION' as const,
    name: '追加延長',
    price: 4000,
    remuneration: 2000,
  };
  jest.mocked(selfRemunerationApi.changes).mockResolvedValue(
    page([
      {
        ...invalidation,
        change_type: 'CORRECTION' as const,
        reason: '延長の付け忘れ',
        after: { ...invalidation.before, items: [item, extension] },
      },
    ])
  );
  render(<CastRemunerationDetailPage />);
  const change = await screen.findByRole('article', { name: /訂正/ });
  expect(valueOf(change, '追加延長（追加）')).toBe('¥2,000');
});
