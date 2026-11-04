import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { remunerationApi, type Day, type Statement } from '../../api';
import { notify } from '@/shared/notify';
import { RemunerationStatementPanel } from '../RemunerationStatementPanel';
jest.mock('../../api', () => ({
  remunerationApi: { statement: jest.fn(), accept: jest.fn() },
}));
jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));
const statement = jest.mocked(remunerationApi.statement);
const accept = jest.mocked(remunerationApi.accept);
const page = { content: [], number: 0, size: 20, total_elements: 0, total_pages: 0 };
const base: Omit<Day, 'business_date' | 'acceptance_state'> = {
  order_amount: 7000,
  daily_amount: 10000,
  closed_duration: 'PT5H',
  attendance_incomplete: false,
  guarantee_status: 'CALCULATED',
  guarantee_amount: 3000,
  bonus_amount: 0,
};
const acceptance = {
  id: 'a1',
  accepted_at: '2026-10-02T00:00:00Z',
  order_amount: 7000,
  guarantee_amount: 3000,
  bonus_amount: 0,
};
const data: Statement = {
  store_id: 1,
  store_name: '店舗',
  person_id: 2,
  name: '本人',
  month: '2026-09',
  generated_at: '2026-10-09T00:00:00Z',
  order_total: 29000,
  known_guarantee_total: 6000,
  guarantee_total: 6000,
  bonus_total: 500,
  total: 35500,
  accepted_days: 1,
  acceptable_days: 3,
  days: [
    {
      ...base,
      business_date: '2026-09-27',
      acceptance_state: 'NOT_ACCEPTED',
      content_digest: 'd27',
    },
    {
      ...base,
      business_date: '2026-09-28',
      acceptance_state: 'ACCEPTED',
      content_digest: 'd28',
      latest_acceptance: acceptance,
    },
    {
      ...base,
      business_date: '2026-09-29',
      order_amount: 8000,
      guarantee_amount: 2000,
      bonus_amount: 500,
      acceptance_state: 'CHANGED_AFTER_ACCEPTANCE',
      content_digest: 'd29',
      latest_acceptance: acceptance,
    },
    {
      ...base,
      business_date: '2026-09-30',
      guarantee_status: 'PENDING_ATTENDANCE',
      guarantee_amount: undefined,
      attendance_incomplete: true,
      acceptance_state: 'NOT_ACCEPTABLE',
      not_acceptable_reason: 'GUARANTEE_UNDETERMINED',
      latest_acceptance: acceptance,
    },
  ],
  orders: page,
  bonus_awards: page,
};
const row = (date: string) => screen.getByRole('row', { name: new RegExp(date) });
async function open(scope: 'self' | 'store') {
  render(
    <RemunerationStatementPanel
      scope={scope === 'self' ? { scope: 'self', storeId: 1 } : { scope: 'store', personId: 2 }}
      month="2026-09"
      canView
      canManage={false}
    />
  );
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  await screen.findByText('店舗 / 本人 / 2026-09');
}
beforeEach(() => {
  jest.clearAllMocks();
  statement.mockResolvedValue(data);
});
it('本人の日別行に四つの状態と承諾の操作、変更ありの差分を示す', async () => {
  await open('self');
  // 狭い幅で横に送らなくても、営業日と承諾の状態・操作が並んで見えるようにする
  const [daily] = screen.getAllByRole('table');
  expect(
    within(daily)
      .getAllByRole('columnheader')
      .slice(0, 2)
      .map(h => h.textContent)
  ).toEqual(['営業日', '承諾']);
  expect(within(row('2026-09-27')).getByText('未承諾')).toBeInTheDocument();
  expect(
    within(row('2026-09-27')).getByRole('button', { name: '2026-09-27 を承諾' })
  ).toBeEnabled();
  expect(within(row('2026-09-28')).getByText('承諾済み')).toBeInTheDocument();
  expect(within(row('2026-09-28')).queryByRole('button')).not.toBeInTheDocument();
  const changed = row('2026-09-29');
  expect(within(changed).getByText('承諾後に変更あり')).toBeInTheDocument();
  expect(within(changed).getByText('受注報酬 ¥7,000 → ¥8,000')).toBeInTheDocument();
  expect(within(changed).getByText('保証不足分 ¥3,000 → ¥2,000')).toBeInTheDocument();
  expect(within(changed).getByText('ボーナス ¥0 → ¥500')).toBeInTheDocument();
  expect(within(changed).getByRole('button', { name: '2026-09-29 を再承諾' })).toBeEnabled();
  const pending = row('2026-09-30');
  expect(within(pending).getByText('承諾不可')).toBeInTheDocument();
  expect(within(pending).getByText('退勤記録待ち・日額未設定')).toBeInTheDocument();
  expect(within(pending).getByText(/最新の承諾/)).toBeInTheDocument();
  expect(within(pending).queryByRole('button')).not.toBeInTheDocument();
  expect(screen.getByText('承諾済み 1 日 / 承諾できる日 3 日')).toBeInTheDocument();
  expect(screen.queryByText(/確定/)).not.toBeInTheDocument();
});
it('額が変わらず内訳だけが変わった日はその旨を示す', async () => {
  statement.mockResolvedValue({
    ...data,
    days: [{ ...data.days[2], order_amount: 7000, guarantee_amount: 3000, bonus_amount: 0 }],
  });
  await open('self');
  expect(within(row('2026-09-29')).getByText('額は同じで内訳が変わりました')).toBeInTheDocument();
});
it('承諾は見ている内容の要約を送り、明細を取り直す', async () => {
  accept.mockResolvedValue(acceptance);
  await open('self');
  fireEvent.click(screen.getByRole('button', { name: '2026-09-27 を承諾' }));
  await waitFor(() =>
    expect(accept).toHaveBeenCalledWith({
      store_id: 1,
      business_date: '2026-09-27',
      content_digest: 'd27',
    })
  );
  await waitFor(() => expect(statement).toHaveBeenCalledTimes(2));
  expect(notify.success).toHaveBeenCalledWith('2026-09-27 の明細を承諾しました');
});
it('内容が変わっていた承諾（409）は明細を取り直して現在の内容を示す', async () => {
  accept.mockRejectedValue({ response: { status: 409, data: { error: '変わっています' } } });
  await open('self');
  fireEvent.click(screen.getByRole('button', { name: '2026-09-29 を再承諾' }));
  await waitFor(() => expect(statement).toHaveBeenCalledTimes(2));
  expect(notify.warning).toHaveBeenCalledWith(
    '明細の内容が変わっていたため、最新の明細を表示しました。内容を確かめてから承諾してください'
  );
  expect(notify.success).not.toHaveBeenCalled();
});
it('店舗側は承諾状態を読むだけで操作を持たない', async () => {
  await open('store');
  expect(within(row('2026-09-27')).getByText('未承諾')).toBeInTheDocument();
  expect(within(row('2026-09-29')).getByText('承諾後に変更あり')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /承諾$/ })).not.toBeInTheDocument();
  expect(screen.getByText('承諾済み 1 日 / 承諾できる日 3 日')).toBeInTheDocument();
});
