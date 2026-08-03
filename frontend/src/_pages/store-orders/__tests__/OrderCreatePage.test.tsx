import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CreateOrderPage from '../ui/OrderCreatePage';
import { castApi } from '@/entities/cast';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    create: jest.fn(),
    listReceptionists: jest.fn(),
  },
}));

jest.mock('@/entities/cast', () => ({
  castApi: {
    get: jest.fn(),
    list: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

/** フォームの必須項目（受付・キャスト）を満たして送信まで進める。 */
async function fillRequiredAndRender() {
  render(<CreateOrderPage />);
  fireEvent.keyDown(await screen.findByRole('combobox', { name: /受付(?!経路)/ }), {
    key: 'ArrowDown',
  });
  fireEvent.click(await screen.findByRole('option', { name: '受付花子' }));
  fireEvent.change(screen.getByLabelText('キャスト *'), { target: { value: '花子' } });
  fireEvent.click(await screen.findByRole('option', { name: /ID: cast-1/ }));
}

describe('新規オーダー登録の送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    (castApi as jest.Mocked<typeof castApi>).list.mockResolvedValue({
      rows: [{ id: 'cast-1', name: '花子' }],
      total: 1,
      page: 0,
      size: 10,
    });
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
