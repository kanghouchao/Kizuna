import { startTransition } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import CustomerCreatePage from '../ui/CustomerCreatePage';
import { customerApi } from '@/entities/customer';
import { notify } from '@/shared/notify';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
}));
jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useCurrentStoreId: () => '1',
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
  }),
}));
jest.mock('@/entities/customer', () => ({ customerApi: { create: jest.fn() } }));
jest.mock('@/shared/notify', () => ({ notify: { success: jest.fn(), error: jest.fn() } }));

beforeEach(() => jest.clearAllMocks());

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

test('応答が返ってから遷移が終わるまで、送信ボタンを押せないままにする', async () => {
  let respond!: (value: { id: string }) => void;
  (customerApi.create as jest.Mock).mockImplementationOnce(
    () => new Promise(resolve => (respond = resolve))
  );
  let arrive!: () => void;
  // router.push と同じく何も返さず、自分の transition の中で画面を進める
  mockPush.mockImplementationOnce(() => {
    startTransition(async () => {
      await new Promise<void>(resolve => (arrive = resolve));
    });
  });
  render(<CustomerCreatePage />);
  fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '遷移待ち' } });
  fireEvent.click(screen.getByRole('button', { name: '保存する' }));
  await waitFor(() => expect(customerApi.create).toHaveBeenCalled());
  expect(screen.getByRole('button', { name: '保存中...' })).toBeDisabled();

  await act(async () => respond({ id: 'new' }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/store/1/customers'));
  expect(screen.getByRole('button', { name: '保存中...' })).toBeDisabled();

  await act(async () => arrive());
  expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled();
});

test('同じフレームで二度送信しても、登録は一度しか送らない', async () => {
  (customerApi.create as jest.Mock).mockResolvedValue({ id: 'new' });
  render(<CustomerCreatePage />);
  fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '二度押し' } });
  const submit = screen.getByRole('button', { name: '保存する' });
  act(() => {
    fireEvent.click(submit);
    fireEvent.click(submit);
  });
  await waitFor(() => expect(mockPush).toHaveBeenCalled());
  await waitFor(() => expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled());
  expect(customerApi.create).toHaveBeenCalledTimes(1);
});
