import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { OrderPreview } from '@/entities/order';
import { useOrderConfirmation } from '../ui/useOrderConfirmation';

let mockStore = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStore }) }));
beforeEach(() => {
  mockStore = '1';
});
const first: OrderPreview = {
  total_duration_minutes: 60,
  total_remuneration: 7000,
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
