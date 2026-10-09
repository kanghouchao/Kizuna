import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { remunerationApi, type Statement } from '../../api';
import { readTokenClaims } from '@/shared/lib';
import { RemunerationStatementPanel } from '../RemunerationStatementPanel';
import { RemunerationEditor } from '../RemunerationEditor';
jest.mock('../../api', () => ({
  remunerationApi: { statement: jest.fn(), createBonus: jest.fn() },
}));
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));
const originalRandomUUID = crypto.randomUUID;
afterEach(() => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: originalRandomUUID });
});
const statement = jest.mocked(remunerationApi.statement);
const page = { content: [], number: 0, size: 20, total_elements: 0, total_pages: 0 };
const data: Statement = {
  store_id: 1,
  store_name: '店舗',
  person_id: 2,
  name: '本人',
  month: '2026-09',
  generated_at: '2026-10-01T00:00:00Z',
  order_total: 7000,
  known_guarantee_total: 0,
  bonus_total: 1000,
  days: [
    {
      business_date: '2026-09-30',
      order_amount: 7000,
      closed_duration: 'PT3H',
      attendance_incomplete: true,
      guarantee_status: 'PENDING_ATTENDANCE',
      bonus_amount: 1000,
    },
  ],
  orders: page,
  bonus_awards: page,
};
beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(readTokenClaims).mockReturnValue(null);
  statement.mockResolvedValue(data);
});
it('未終了時は既知小計を示し完全合計を捏造しない', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  expect(await screen.findByText('退勤記録待ち')).toBeInTheDocument();
  expect(screen.getByText(/現在計算できる保証の小計/)).toBeInTheDocument();
  expect(screen.queryByText('¥8,000')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'ボーナスを記録' })).not.toBeInTheDocument();
});
it('停止日の未終了表示はゼロの保証合計と両立する', async () => {
  statement.mockResolvedValue({
    ...data,
    guarantee_total: 0,
    total: 8000,
    days: [{ ...data.days[0], guarantee_status: 'STOPPED', guarantee_amount: 0 }],
  });
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  expect(await screen.findByText('保証停止')).toBeInTheDocument();
  expect(screen.getByText('未終了の記録あり')).toBeInTheDocument();
  expect(screen.getByText('¥8,000')).toBeInTheDocument();
  expect(screen.queryByText(/現在計算できる保証の小計/)).not.toBeInTheDocument();
});
it('参照権限のない店舗利用者に追加金額の導線を出さない', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'store', personId: 2 }} month="2026-09" />);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
  expect(statement).not.toHaveBeenCalled();
});
it('取得失敗時は旧合計を残さず領域内で再試行する', async () => {
  render(<RemunerationStatementPanel scope={{ scope: 'self', storeId: 1 }} month="2026-09" />);
  fireEvent.click(await screen.findByRole('button', { name: '保証・ボーナスを含む月次明細' }));
  await screen.findByText('退勤記録待ち');
  statement.mockRejectedValueOnce(new Error('failed'));
  fireEvent.click(screen.getByRole('button', { name: '内訳を再照会' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('取得できません');
  expect(screen.queryByText('¥7,000')).not.toBeInTheDocument();
});
it('HTTP環境でもボーナス保存に帰属日と理由とUUIDを送信する', async () => {
  Object.defineProperty(crypto, 'randomUUID', { configurable: true, value: undefined });
  jest.mocked(remunerationApi.createBonus).mockResolvedValue();
  const saved = jest.fn();
  render(
    <RemunerationEditor
      open
      personId={2}
      target={{ kind: 'bonus' }}
      onClose={jest.fn()}
      onSaved={saved}
    />
  );
  fireEvent.change(screen.getByLabelText('帰属日'), { target: { value: '2026-09-30' } });
  fireEvent.change(screen.getByLabelText('付与額（円）'), { target: { value: '1000' } });
  fireEvent.change(screen.getByLabelText('理由・説明'), { target: { value: '付与理由' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(remunerationApi.createBonus).toHaveBeenCalledWith(2, {
    award_date: '2026-09-30',
    amount: 1000,
    reason: '付与理由',
    request_id: expect.stringMatching(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    ),
  });
});
