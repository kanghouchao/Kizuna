import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OrderForm, OrderFormData } from '../ui/OrderForm';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
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

function renderForm() {
  const onSubmit = jest.fn<void, [OrderFormData]>();
  const view = render(<OrderForm onSubmit={onSubmit} isSubmitting={false} />);
  return { ...view, onSubmit };
}

async function submitAndGetBody(onSubmit: jest.Mock) {
  fireEvent.click(screen.getByRole('button', { name: '登録する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  return onSubmit.mock.calls[0][0] as OrderFormData;
}

/** キーボードで開く経路のみを使う（ポインタ系 API は jsdom に無い）。 */
async function pickOption(comboboxName: string, optionName: string) {
  fireEvent.keyDown(await screen.findByRole('combobox', { name: comboboxName }), {
    key: 'ArrowDown',
  });
  fireEvent.click(await screen.findByRole('option', { name: optionName }));
}

describe('オーダーフォームのセレクト配線と送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
  });

  it('未操作の既定値が型ごとそのまま送られること', async () => {
    const { onSubmit } = renderForm();
    // 受付一覧の解決を待ってから送信する（非同期の setState が入るため）
    await waitFor(() => expect(mockedOrderApi.listReceptionists).toHaveBeenCalled());

    const body = await submitAndGetBody(onSubmit);

    expect(body.receptionistId).toBe('');
    expect(body.classification).toBe('ーー');
    expect(body.hasPet).toBe(false);
    expect(body.courseMinutes).toBe(60);
    expect(body.discountName).toBe('');
  });

  it('受付の選択が番兵を経て素の ID 文字列で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('受付', '受付花子');
    const body = await submitAndGetBody(onSubmit);

    // 番兵値 __none__ が漏れず、選んだ受付の id が文字列で載ること
    expect(body.receptionistId).toBe('7');
  });

  it('受付を未選択へ戻すと番兵ではなく空文字で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('受付', '受付花子');
    await pickOption('受付', '－－－');
    const body = await submitAndGetBody(onSubmit);

    expect(body.receptionistId).toBe('');
  });

  it('区分の選択がそのままの文字列で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('区分', 'ラブホ');
    const body = await submitAndGetBody(onSubmit);

    expect(body.classification).toBe('ラブホ');
  });

  it('ペット有無が文字列ではなく真偽値へ復元されて送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('ペット有無', 'あり');
    const body = await submitAndGetBody(onSubmit);

    expect(body.hasPet).toBe(true);
    expect(typeof body.hasPet).toBe('boolean');
  });

  it('コース分が文字列ではなく数値へ復元されて送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('ｺｰｽ(分)', '120');
    const body = await submitAndGetBody(onSubmit);

    expect(body.courseMinutes).toBe(120);
    expect(typeof body.courseMinutes).toBe('number');
  });

  it('割引の選択と解除が番兵を経て文字列・空文字で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('割引', '一番最初割');
    const body = await submitAndGetBody(onSubmit);

    expect(body.discountName).toBe('一番最初割');
  });

  it('割引を「なし」へ戻すと番兵ではなく空文字で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption('割引', '一番最初割');
    await pickOption('割引', 'なし');
    const body = await submitAndGetBody(onSubmit);

    expect(body.discountName).toBe('');
  });
});
