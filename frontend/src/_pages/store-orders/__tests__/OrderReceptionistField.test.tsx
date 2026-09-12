import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { orderApi, OrderReceptionist } from '@/entities/order';
import { Form } from '@/shared/ui';
import { OrderReceptionistField } from '../ui/OrderReceptionistField';

let mockStoreId = '1';
jest.mock('next/navigation', () => ({ useParams: () => ({ storeId: mockStoreId }) }));
jest.mock('@/entities/order', () => ({ orderApi: { listReceptionists: jest.fn() } }));
const list = jest.mocked(orderApi.listReceptionists);
const submit = jest.fn();
function Harness({ scene = 'create' }: { scene?: 'create' | 'edit' | 'confirm' }) {
  const form = useForm({ defaultValues: { receptionist_id: '' } });
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(submit)}>
        {scene === 'edit' ? (
          <OrderReceptionistField scene="edit" originalId={undefined} originalName={undefined} />
        ) : (
          <OrderReceptionistField scene={scene} />
        )}
        <button type="submit">送信</button>
      </form>
    </Form>
  );
}
async function pick(name: string) {
  fireEvent.click(screen.getByRole('combobox'));
  const item = await screen.findByRole('option', { name });
  fireEvent.pointerDown(item);
  fireEvent.click(item);
}
beforeEach(() => {
  jest.clearAllMocks();
  mockStoreId = '1';
  list.mockResolvedValue([]);
});

test.each([
  ['create', '自分（既定）'],
  ['edit', '未設定'],
  ['confirm', '未設定（自分が受付なら自動で補われます）'],
] as const)('%s の未選択文言と読み込み・候補なしを区別する', async (scene, label) => {
  let resolve!: (rows: OrderReceptionist[]) => void;
  list.mockReturnValueOnce(
    new Promise(done => {
      resolve = done;
    })
  );
  render(<Harness scene={scene} />);
  expect(screen.getByRole('combobox')).toHaveTextContent(label);
  expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '送信' }));
  await waitFor(() =>
    expect(submit).toHaveBeenCalledWith({ receptionist_id: '' }, expect.anything())
  );
  await act(async () => resolve([]));
  expect(screen.getByText('受付担当の候補がいません')).toBeInTheDocument();
  expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument();
});

test('背景取得失敗で候補を消しても選択した値と名前を保ち、再試行後も自動変更しない', async () => {
  list.mockResolvedValueOnce([{ id: 7, display_name: '受付花子' }]);
  const { rerender } = render(<Harness />);
  await pick('受付花子');
  let reject!: (error: Error) => void;
  list.mockReturnValueOnce(
    new Promise((_, fail) => {
      reject = fail;
    })
  );
  mockStoreId = '2';
  rerender(<Harness />);
  expect(screen.getByRole('combobox')).toHaveTextContent('受付花子');
  await act(async () => reject(new Error('offline')));
  expect(await screen.findByRole('alert')).toHaveTextContent('受付担当者の取得に失敗しました');
  expect(screen.getByRole('combobox')).toHaveTextContent('受付花子');
  fireEvent.click(screen.getByRole('combobox'));
  expect(screen.queryByRole('option', { name: '受付花子' })).not.toBeInTheDocument();
  fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' });
  fireEvent.click(screen.getByRole('button', { name: '送信' }));
  await waitFor(() =>
    expect(submit).toHaveBeenCalledWith({ receptionist_id: '7' }, expect.anything())
  );
  list.mockResolvedValueOnce([{ id: 9 }]);
  fireEvent.click(screen.getByRole('button', { name: '再試行' }));
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  expect(screen.getByRole('combobox')).toHaveTextContent('受付花子');
  fireEvent.click(screen.getByRole('button', { name: '送信' }));
  await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
  expect(submit.mock.calls[1][0]).toEqual({ receptionist_id: '7' });
  await pick('受付担当 ID: 9');
  expect(screen.getByRole('combobox')).toHaveTextContent('受付担当 ID: 9');
});
