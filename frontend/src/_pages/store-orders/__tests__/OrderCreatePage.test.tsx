import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CreateOrderPage from '../ui/OrderCreatePage';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    create: jest.fn(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

/**
 * フォームの必須項目（受付・キャスト）を満たして送信まで進める。
 *
 * キャストを先に選ぶ。Radix Select を開いた直後は Popover が開かない。
 */
async function fillRequiredAndRender() {
  render(<CreateOrderPage />);
  fireEvent.click(await screen.findByRole('combobox', { name: /キャスト/ }));
  fireEvent.click(await screen.findByRole('option', { name: /ID: cast-1/ }));
  fireEvent.keyDown(await screen.findByRole('combobox', { name: /受付(?!経路)/ }), {
    key: 'ArrowDown',
  });
  fireEvent.click(await screen.findByRole('option', { name: '受付花子' }));
}

describe('新規オーダー登録の送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);
    mockedOrderApi.create.mockResolvedValue({});
  });

  it('人数を空欄にすると pax を送らない（Number("") の 0 で @Min(1) に撥ねられない）', async () => {
    await fillRequiredAndRender();

    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    expect(mockedOrderApi.create.mock.calls[0][0].pax).toBeUndefined();
  });

  it('入力した人数は数値として送る', async () => {
    await fillRequiredAndRender();

    fireEvent.change(screen.getByLabelText('人数'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    expect(mockedOrderApi.create.mock.calls[0][0].pax).toBe(3);
  });
});
