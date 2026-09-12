import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CreateOrderPage from '../ui/OrderCreatePage';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
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
 * キャストを先に選ぶ。指名の選択が開いている間は受付の選択を開けない。
 */
async function fillRequiredAndRender(selectReceptionist = true) {
  render(<CreateOrderPage />);
  fireEvent.click(await screen.findByRole('combobox', { name: /キャスト/ }));
  const option = await screen.findByRole('option', { name: /ID: cast-1/ });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
  if (!selectReceptionist) return;
  fireEvent.click(await screen.findByRole('combobox', { name: /受付(?!経路)/ }));
  const option2 = await screen.findByRole('option', { name: '受付花子' });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option2);
  fireEvent.click(option2);
}

describe('新規オーダー登録の送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);
    mockedOrderApi.create.mockResolvedValue({ fee_lines: [] });
  });

  it('受付を省略しても、数値と時刻を変換し snake_case の項目で送信する', async () => {
    await fillRequiredAndRender(false);
    fireEvent.change(screen.getByLabelText('お客様名'), { target: { value: '山田' } });
    fireEvent.change(screen.getByLabelText('建物'), { target: { value: '第一ビル' } });
    fireEvent.change(screen.getByLabelText('営業日'), { target: { value: '2026-09-12' } });
    fireEvent.change(document.querySelector('input[name="arrival_scheduled_start_time"]')!, {
      target: { value: '19:30' },
    });
    fireEvent.change(document.querySelector('input[name="arrival_scheduled_end_time"]')!, {
      target: { value: '20:00' },
    });
    fireEvent.change(screen.getByLabelText('延長'), { target: { value: '30' } });
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));
    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    const body = mockedOrderApi.create.mock.calls[0][0];
    expect(body).toMatchObject({
      customer_name: '山田',
      building_name: '第一ビル',
      business_date: '2026-09-12',
      arrival_scheduled_start_time: '19:30:00',
      arrival_scheduled_end_time: '20:00:00',
      pax: 1,
      course_minutes: 60,
      extension_minutes: 30,
      reception_route: 'PHONE',
      cast_id: 'cast-1',
    });
    expect(JSON.parse(JSON.stringify(body))).not.toHaveProperty('receptionist_id');
    expect(Object.keys(body).every(key => !/[A-Z]/.test(key))).toBe(true);
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
    expect(mockedOrderApi.create.mock.calls[0][0].receptionist_id).toBe(7);
  });

  // 指名の焦点要素は popup の中の入力ではなく引き金の button。文言を出すだけでは
  // 「どの欄が」が読み上げ環境へ届かず、焦点も動かない。
  it('キャスト未選択の文言が引き金と結び付き、焦点がそこへ移ること', async () => {
    render(<CreateOrderPage />);

    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    const message = await screen.findByText('キャストを候補から選択してください');
    const trigger = screen.getByRole('combobox', { name: /キャスト/ });
    expect(trigger).toHaveAttribute('aria-invalid', 'true');
    await waitFor(() => expect(trigger).toHaveFocus());
    expect(trigger).toHaveAttribute('aria-describedby', expect.stringContaining(message.id));
    expect(mockedOrderApi.create).not.toHaveBeenCalled();
  });

  // aria-invalid は「今まちがっている」しか言わない。「入れる必要がある」は誤りが起きる前から
  // 要る情報で、引き金は button なので原生 required では持てない。
  it('キャストの引き金が最初から必須を名乗ること', async () => {
    render(<CreateOrderPage />);

    const trigger = await screen.findByRole('combobox', { name: /キャスト/ });
    expect(trigger).toHaveAttribute('aria-required', 'true');
  });
});
