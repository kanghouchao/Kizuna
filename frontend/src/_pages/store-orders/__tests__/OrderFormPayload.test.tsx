import { StrictMode } from 'react';
import { render, screen, fireEvent, waitFor, act, within } from '@testing-library/react';
import { OrderForm, OrderFormData } from '../ui/OrderForm';
import * as castEntity from '@/entities/cast';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
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
  // キャストもサーバ側が @NotBlank。ここでの主題ではないため初期値で満たしておく。
  const view = render(
    <OrderForm initialData={{ castId: 'cast-1' }} onSubmit={onSubmit} isSubmitting={false} />
  );
  return { ...view, onSubmit };
}

async function submitAndGetBody(onSubmit: jest.Mock) {
  fireEvent.click(screen.getByRole('button', { name: '登録する' }));
  await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
  return onSubmit.mock.calls[0][0] as OrderFormData;
}

/** 受付はサーバ側が @NotNull。送信まで進めるテストは先に選んでおく。 */
async function selectReceptionist() {
  await pickOption(/受付(?!経路)/, '受付花子');
}

/** キーボードで開く経路のみを使う（ポインタ系 API は jsdom に無い）。 */
async function pickOption(comboboxName: string | RegExp, optionName: string) {
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
    mockedOrderApi.listCastCandidates.mockResolvedValue([]);
  });

  it('受付以外を未操作のまま送ると既定値が型ごとそのまま送られること', async () => {
    const { onSubmit } = renderForm();
    // 受付一覧の解決を待ってから送信する（非同期の setState が入るため）
    await waitFor(() => expect(mockedOrderApi.listReceptionists).toHaveBeenCalled());
    await selectReceptionist();

    const body = await submitAndGetBody(onSubmit);

    expect(body.classification).toBe('ーー');
    expect(body.hasPet).toBe(false);
    expect(body.courseMinutes).toBe(60);
    expect(body.discountName).toBe('');
  });

  it('受付の選択が番兵を経て素の ID 文字列で送られること', async () => {
    const { onSubmit } = renderForm();

    await pickOption(/受付(?!経路)/, '受付花子');
    const body = await submitAndGetBody(onSubmit);

    // 番兵値 __none__ が漏れず、選んだ受付の id が文字列で載ること
    expect(body.receptionistId).toBe('7');
  });

  it('受付を未選択へ戻すと送信されないこと', async () => {
    const { onSubmit } = renderForm();

    await pickOption(/受付(?!経路)/, '受付花子');
    await pickOption(/受付(?!経路)/, '－－－');
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    // 受付は @NotNull。未選択のまま送ると 400 になるため、フォーム側で止める。
    await waitFor(() => expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled());
    expect(onSubmit).not.toHaveBeenCalled();
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

    expect(body.hasPet).toBe(true);
    expect(typeof body.hasPet).toBe('boolean');
  });

  it('コース分が文字列ではなく数値へ復元されて送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('ｺｰｽ(分)', '120');
    const body = await submitAndGetBody(onSubmit);

    expect(body.courseMinutes).toBe(120);
    expect(typeof body.courseMinutes).toBe('number');
  });

  it('割引の選択と解除が番兵を経て文字列・空文字で送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('割引', '一番最初割');
    const body = await submitAndGetBody(onSubmit);

    expect(body.discountName).toBe('一番最初割');
  });

  it('割引を「なし」へ戻すと番兵ではなく空文字で送られること', async () => {
    const { onSubmit } = renderForm();

    await selectReceptionist();
    await pickOption('割引', '一番最初割');
    await pickOption('割引', 'なし');
    const body = await submitAndGetBody(onSubmit);

    expect(body.discountName).toBe('');
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

  it('候補を選ぶと castId がその id で送られること', async () => {
    const onSubmit = jest.fn<void, [OrderFormData]>();
    render(
      <OrderForm initialData={{ receptionistId: '7' }} onSubmit={onSubmit} isSubmitting={false} />
    );

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
    expect(onSubmit.mock.calls[0][0].castId).toBe('cast-1');
  });

  it('候補を選ばないまま送ると、キャスト未選択として止められること', async () => {
    // キャストは @NotBlank。絞り込んだだけでは選択にならない
    const onSubmit = jest.fn<void, [OrderFormData]>();
    render(
      <OrderForm initialData={{ receptionistId: '7' }} onSubmit={onSubmit} isSubmitting={false} />
    );

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

describe('オーダーフォームの編集時の指名表示', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([]);
  });

  it('渡された castName をそのまま出し、キャスト管理 API を一切呼ばないこと', async () => {
    // 名前を id から取り直すと CAST_MANAGE が要り、受注担当だけのロールでは 403 になる。
    // 受注の応答が cast_name を持っているので、呼び出し側から受け取れば足りる
    render(
      <OrderForm
        initialData={{ castId: 'cast-1' }}
        castName="あや"
        onSubmit={jest.fn()}
        isSubmitting={false}
      />
    );
    await waitFor(() => expect(mockedOrderApi.listReceptionists).toHaveBeenCalled());

    expect(screen.getByRole('combobox', { name: /キャスト/ })).toHaveTextContent('あや');
    expect(castEntity.castApi.get as jest.Mock).not.toHaveBeenCalled();
    expect(castEntity.castApi.list as jest.Mock).not.toHaveBeenCalled();
  });
});

describe('オーダーフォームの受付候補の取得失敗', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listCastCandidates.mockResolvedValue([]);
  });

  it('候補が取れなければ欄の傍で失敗を名乗り、送信は塞がず、再試行で回復すること', async () => {
    mockedOrderApi.listReceptionists.mockRejectedValueOnce(new Error('boom'));
    const { onSubmit } = renderForm();

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('受付担当者の取得に失敗しました')).toBeInTheDocument();

    // 系の故障を利用者の入力ミスとして扱わない — 押せるし、押した結果は欄の傍の必須検証が言う
    const submit = screen.getByRole('button', { name: '登録する' });
    expect(submit).toBeEnabled();
    fireEvent.click(submit);
    expect(await screen.findByText('受付を選択してください')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());

    await selectReceptionist();
    const body = await submitAndGetBody(onSubmit);
    expect(body.receptionistId).toBe('7');
  });

  // Strict Mode は mount effect を二度走らせるので取得が二重に飛ぶ。失敗が選択肢をクリアする
  // 以上、遅れて着いた古い失敗が新しい成功を消してはいけない
  it('二重 mount で古い失敗が後から着いても、新しい成功を消さないこと', async () => {
    let failStale = (): void => {};
    mockedOrderApi.listReceptionists
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          failStale = () => reject(new Error('stale'));
        })
      )
      .mockResolvedValue([{ id: 7, display_name: '受付花子' }]);

    render(
      <StrictMode>
        <OrderForm
          initialData={{ castId: 'cast-1' }}
          onSubmit={jest.fn<void, [OrderFormData]>()}
          isSubmitting={false}
        />
      </StrictMode>
    );

    // 候補から選べた時点で、2 度目の成功が着いている
    await selectReceptionist();

    await act(async () => {
      failStale();
    });

    // 引き金の文言は候補一覧から引かれる。古い失敗が候補を消すと選択ごと表示が戻る
    expect(screen.getByRole('combobox', { name: /受付(?!経路)/ })).toHaveTextContent('受付花子');
    expect(screen.queryByText('受付担当者の取得に失敗しました')).not.toBeInTheDocument();
  });
});
