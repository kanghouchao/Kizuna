import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { OrderForm, OrderFormData } from '../ui/OrderForm';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
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
  await pickOption(/キャスト/, /花子/);
  fireEvent.click(screen.getByRole('button', { name: '登録する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  return onSubmit.mock.calls[0][0] as OrderFormData;
}

/** 受付の既定は「自分」（＝未選択）。誰を選んだかが主題のテストだけが明示的に選ぶ。 */
async function selectReceptionist() {
  await pickOption(/受付(?!経路)/, '受付花子');
}

async function pickOption(comboboxName: string | RegExp, optionName: string | RegExp) {
  fireEvent.click(await screen.findByRole('combobox', { name: comboboxName }));
  const option = await screen.findByRole('option', { name: optionName });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

describe('オーダーフォームのセレクト配線と送信ペイロード', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);
  });

  it('受付・キャスト以外を未操作のまま送ると既定値が型ごとそのまま送られること', async () => {
    const { onSubmit } = renderForm();
    // 受付一覧の解決を待ってから送信する（非同期の setState が入るため）
    await waitFor(() => expect(mockedOrderApi.listReceptionists).toHaveBeenCalled());
    await selectReceptionist();

    const body = await submitAndGetBody(onSubmit);

    expect(body.classification).toBe('ーー');
    expect(body.has_pet).toBe(false);
    expect(body.course_minutes).toBe(60);
    expect(body.course_name).toBe('');
    expect(body.fee_lines).toEqual([]);
  });

  it('受付の選択が番兵を経て素の ID 文字列で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption(/受付(?!経路)/, '受付花子');
    const body = await submitAndGetBody(onSubmit);

    // 番兵値 __none__ が漏れず、選んだ受付の id が文字列で載ること
    expect(body.receptionist_id).toBe('7');
  });

  it('受付を未選択へ戻すと空欄のまま送信できること（実行者本人が受付担当になる）', async () => {
    const { onSubmit } = renderForm();

    await pickOption(/受付(?!経路)/, '受付花子');
    await pickOption(/受付(?!経路)/, '自分（既定）');
    const body = await submitAndGetBody(onSubmit);

    // 空欄は「自分」の意。ページ側が項目ごと落とし、サーバが実行者本人を受付担当に据える
    expect(body.receptionist_id).toBe('');
  });

  it('区分の選択がそのままの文字列で送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('区分', 'ラブホ');
    const body = await submitAndGetBody(onSubmit);

    expect(body.classification).toBe('ラブホ');
  });

  it('ペット有無が文字列ではなく真偽値へ復元されて送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('ペット有無', 'あり');
    const body = await submitAndGetBody(onSubmit);

    expect(body.has_pet).toBe(true);
    expect(typeof body.has_pet).toBe('boolean');
  });

  it('コース分が文字列ではなく数値へ復元されて送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('ｺｰｽ(分)', '120');
    const body = await submitAndGetBody(onSubmit);

    expect(body.course_minutes).toBe(120);
    expect(typeof body.course_minutes).toBe('number');
  });

  it('追加した明細が種別・名称・金額の行としてそのまま送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    fireEvent.click(screen.getByRole('button', { name: '明細を追加' }));
    fireEvent.change(await screen.findByLabelText('明細1の名称'), {
      target: { value: '指名オプション' },
    });
    fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: '3000' } });
    const body = await submitAndGetBody(onSubmit);

    // 金額は表示上の値。符号は種別が表すので、画面は正値しか受けない
    expect(body.fee_lines).toEqual([{ kind: 'OPTION', name: '指名オプション', amount: 3000 }]);
  });

  it('削除した明細が送信から消えること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    fireEvent.click(screen.getByRole('button', { name: '明細を追加' }));
    fireEvent.change(await screen.findByLabelText('明細1の金額'), { target: { value: '3000' } });
    fireEvent.click(screen.getByRole('button', { name: '明細1を削除' }));
    const body = await submitAndGetBody(onSubmit);

    expect(body.fee_lines).toEqual([]);
  });
});

describe('オーダーフォームのキャスト候補リストの選択配線', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  /** キャストの選択を開き、候補取得まで進める。 */
  const openPicker = async () => {
    fireEvent.click(screen.getByRole('combobox', { name: /キャスト/ }));
    await act(async () => {
      jest.advanceTimersByTime(300);
    });
  };

  const submit = async () => {
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));
    await act(async () => {
      jest.advanceTimersByTime(0);
    });
  };

  it('候補を選ぶと cast_id がその id で送られること', async () => {
    const onSubmit = jest.fn<void, [OrderFormData]>();
    render(<OrderForm onSubmit={onSubmit} isSubmitting={false} />);

    await openPicker();
    fireEvent.change(screen.getByPlaceholderText('名前で検索'), { target: { value: '花' } });
    await act(async () => {
      jest.advanceTimersByTime(300);
    });
    const option = screen.getByRole('option', { name: /花子/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);

    expect(screen.getByRole('combobox', { name: /キャスト/ })).toHaveTextContent('花子');
    await submit();
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit.mock.calls[0][0].cast_id).toBe('cast-1');
  });

  it('候補を選ばないまま送ると、キャスト未選択として止められること', async () => {
    // キャストは @NotBlank。絞り込んだだけでは選択にならない
    const onSubmit = jest.fn<void, [OrderFormData]>();
    render(<OrderForm onSubmit={onSubmit} isSubmitting={false} />);

    await openPicker();
    fireEvent.change(screen.getByPlaceholderText('名前で検索'), { target: { value: '花' } });
    await act(async () => {
      jest.advanceTimersByTime(300);
    });
    fireEvent.keyDown(screen.getByPlaceholderText('名前で検索'), { key: 'Escape' });

    await submit();

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText('キャストを候補から選択してください')).toBeInTheDocument();
  });
});
