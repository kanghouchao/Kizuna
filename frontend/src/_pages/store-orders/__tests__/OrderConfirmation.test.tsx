import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { OrderPreview } from '@/entities/order';
import { useOrderConfirmation } from '../ui/useOrderConfirmation';

let mockStore = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStore }) }));
beforeEach(() => {
  mockStore = '1';
});
const first: OrderPreview = {
  point_basis_amount: 12000,
  total_duration_minutes: 60,
  total_remuneration: 7000,
  requires_attention: false,
  unresolved_special_service_count: 0,
  special_services: [],
  confirmation_token: 'first',
  course: {
    service_id: '1',
    revision_id: 'r1',
    revision_number: 1,
    name: '基本',
    duration_minutes: 60,
    price: 12000,
    remuneration: 7000,
    adoption_basis: 'CURRENT_SETTING',
  },
  fee_lines: [],
  total_fee: 10000,
};
function Screen({
  preview,
  save,
}: {
  preview: () => Promise<OrderPreview>;
  save: (token: string) => void;
}) {
  const confirmation = useOrderConfirmation();
  return (
    <>
      {confirmation.dialog}
      <button
        onClick={async () => {
          const token = await confirmation.confirm(preview);
          if (token) save(token);
        }}
      >
        試算する
      </button>
    </>
  );
}
test('明示確認の前に保存せず、再試算の差異を表示する', async () => {
  const preview = jest
    .fn()
    .mockResolvedValueOnce(first)
    .mockResolvedValueOnce({
      ...first,
      confirmation_token: 'second',
      total_fee: 13000,
      course: { ...first.course, price: 15000, remuneration: 9000 },
    });
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByText(/コース料金: ¥12,000/);
  expect(save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenCalledWith('first'));
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByText(/前回の確認内容から変更/);
  expect(screen.getByText(/報酬: ¥7000 → ¥9000/)).toBeInTheDocument();
  fireEvent.click(screen.getByText('入力に戻る'));
  expect(save).toHaveBeenCalledTimes(1);
});

test('店舗が変わると以前の確認内容と未完了の操作を引き継がない', async () => {
  const save = jest.fn();
  const preview = jest.fn().mockResolvedValue(first);
  const { rerender } = render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  fireEvent.click(await screen.findByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByText('この内容を確認して保存');
  mockStore = '2';
  rerender(<Screen preview={preview} save={save} />);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  preview.mockResolvedValue({
    ...first,
    confirmation_token: 'other',
    course: { ...first.course, name: '別店舗' },
  });
  fireEvent.click(screen.getByText('試算する'));
  await screen.findByText(/別店舗/);
  expect(screen.queryByText(/前回の確認内容/)).not.toBeInTheDocument();
  expect(save).toHaveBeenCalledTimes(1);
});

test('加算の改定差異は費用・報酬・版本を示し、再確認するまで保存しない', async () => {
  const line = {
    kind: 'SURCHARGE' as const,
    name: '指名',
    amount: 1000,
    remuneration: 500,
    revision_number: 1,
    system_owned: false,
  };
  const load = jest
    .fn()
    .mockResolvedValueOnce({ ...first, fee_lines: [line] })
    .mockResolvedValueOnce({
      ...first,
      confirmation_token: 'revised',
      total_remuneration: 8000,
      fee_lines: [{ ...line, amount: 2000, remuneration: 1000, revision_number: 2 }],
    });
  const save = jest.fn();
  render(<Screen preview={load} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  fireEvent.click(await screen.findByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText('試算する'));
  expect(
    await screen.findByText(
      /明細1: 指名 \/ 版1 \/ 料金 ¥1000 \/ 報酬 ¥500.*版2 \/ 料金 ¥2000 \/ 報酬 ¥1000/
    )
  ).toBeInTheDocument();
  expect(save).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenLastCalledWith('revised'));
});

test('トークンが同じでも受諾状態の差を表示し、歴史の受諾を未受諾にしない', async () => {
  const item = {
    service_id: 's1',
    revision_id: 'sr1',
    revision_number: 1,
    terms_version: 1,
    name: '特殊',
    charge_type: 'PAID' as const,
    price: 2000,
    remuneration: 1500,
    adoption_basis: 'ACCEPTED_TERMS' as const,
    enrollment_id: 'cast1',
    consent_version: 1,
    requires_attention: false,
  };
  const preview = jest
    .fn()
    .mockResolvedValueOnce({ ...first, special_services: [item] })
    .mockResolvedValueOnce({
      ...first,
      special_services: [{ ...item, current_consent_status: 'RECONFIRMATION_REQUIRED' }],
    });
  const save = jest.fn();
  render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  fireEvent.click(await screen.findByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText('試算する'));
  const comparison = await screen.findByRole('status');
  expect(comparison).toHaveTextContent('受諾時の約定');
  expect(comparison).toHaveTextContent('再受諾待ち');
  expect(comparison).toHaveTextContent('担当在籍 cast1');
  expect(comparison).not.toHaveTextContent('未受諾');
});

test('ポイント利用上限と通常付与基準を確認してから保存する', async () => {
  const save = jest.fn();
  const preview = jest.fn().mockResolvedValue({
    ...first,
    point_basis_amount: 16000,
    total_fee: 13000,
    points: {
      member_linked: true,
      redemption_eligible: true,
      member_code: 'M001',
      point_balance: 5000,
      usage_unit: 100,
      use_points: 3000,
      grant_points: 160,
    },
  });
  render(<Screen preview={preview} save={save} />);
  fireEvent.click(screen.getByText('試算する'));
  expect(await screen.findByText(/利用上限・通常付与基準: ¥16,000/)).toBeInTheDocument();
  expect(save).not.toHaveBeenCalled();
  fireEvent.click(screen.getByText('この内容を確認して保存'));
  await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
});
