import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import CustomerCreatePage from '../ui/CustomerCreatePage';
import { customerApi } from '@/entities/customer';
import { notify } from '@/shared/notify';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));
jest.mock('@/entities/customer', () => ({ customerApi: { create: jest.fn() } }));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));

test('連絡先の登録エラーを具体的に伝え、入力を修正して再送できる', async () => {
  (customerApi.create as jest.Mock)
    .mockRejectedValueOnce({
      response: { data: { error: '日本の有効な電話番号を入力してください' } },
    })
    .mockResolvedValue({ id: 'new' });
  render(<CustomerCreatePage />);
  fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '連絡先検証' } });
  fireEvent.click(screen.getByRole('button', { name: '連絡先を追加' }));
  fireEvent.change(screen.getByLabelText('連絡先の値'), { target: { value: '+12025550123' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() =>
    expect(notify.error).toHaveBeenCalledWith('日本の有効な電話番号を入力してください')
  );
  expect(mockPush).not.toHaveBeenCalled();
  expect(screen.getByLabelText('連絡先の値')).toHaveValue('+12025550123');
  fireEvent.change(screen.getByLabelText('連絡先の値'), { target: { value: '09012345678' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/customers'));
  expect(customerApi.create).toHaveBeenLastCalledWith(
    expect.objectContaining({
      contacts: [{ type: 'PHONE', value: '09012345678' }],
    })
  );
});
