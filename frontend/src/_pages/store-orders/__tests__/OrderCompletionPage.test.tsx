import { confirmPreview, pointsPreview } from '../lib/orderTestSupport';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import OrderCompletionPage from '../ui/OrderCompletionPage';
import { Order, orderApi } from '@/entities/order';
// 伝票 QR はアプリドメインの申領画面を指す。根 layout が渡す値をここで固定する
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  useAppDomain: () => 'kizuna.test',
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

jest.mock('@/entities/order', () => ({
  // 種別表などの定数は実物を通す。丸ごと差し替えると明細の欄が選択肢を組めない
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    ...jest.requireActual('../lib/orderTestSupport').courseApiMocks(),
    get: jest.fn(),
    complete: jest.fn(),
    completionPreview: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

// QR は描画された画像からは中身を読めない。運ぶ値そのものを断言できるよう、値を属性へ出す差し替えにする
jest.mock('qrcode.react', () => ({
  QRCodeSVG: ({ value, ...props }: { value: string; 'aria-label': string; role: string }) => (
    <span data-testid="qr" data-value={value} aria-label={props['aria-label']} role={props.role} />
  ),
}));

const mockReplace = jest.fn();
let mockQuery = '';
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace, back: jest.fn() }),
  useParams: () => ({ id: 'o1' }),
  useSearchParams: () => new URLSearchParams(mockQuery),
}));

const mockedGet = orderApi.get as jest.Mock;
const mockedComplete = orderApi.complete as jest.Mock;
const mockedPreview = orderApi.completionPreview as jest.Mock;

const LIST_QUERY = 'customer_name=%E5%B1%B1%E7%94%B0&sort_key=PAX&desc=true';
const LIST_HREF = `/store/1/orders?${LIST_QUERY}`;

const confirmedOrder: Order = {
  business_contact_permissions: [],
  completion_invalidated: false,
  accrued_remuneration: 0,
  total_duration_minutes: 60,
  total_remuneration: 7000,
  requires_attention: false,
  unresolved_special_service_count: 0,
  special_services: [],
  course: {
    service_id: 'course-1',
    revision_id: 'r1',
    revision_number: 1,
    name: '基本',
    duration_minutes: 60,
    price: 12000,
    remuneration: 7000,
    adoption_basis: 'CURRENT_SETTING' as const,
    adopted_at: '2026-09-15T00:00:00Z',
  },
  fee_lines: [
    { kind: 'CREDIT_SURCHARGE', name: '会計', amount: 0, remuneration: 0, system_owned: false },
  ],
  id: 'o1',
  status: 'CONFIRMED',
  business_date: '2026-08-10',
  customer_name: '山田太郎',
  // 完了は版を必須で運ぶ。詳細の読み口は必ず返すので、播種元の雛形も持つ
  version: 3,
};

const renderPage = () => render(<OrderCompletionPage />);

const conflict = {
  response: {
    status: 409,
    data: {
      error: 'この受注は別の操作者が更新しました。最新の内容を読み直してからやり直してください',
    },
  },
};

/** 会計の内訳を 1 行だけ入れて完了する（各テストの本題は入力側なので、送信までを 1 つにまとめる）。 */
const completeWith = async (amount: string) => {
  fireEvent.change(await screen.findByLabelText('明細1の名称'), { target: { value: '会計' } });
  fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: amount } });
  fireEvent.click(screen.getByRole('button', { name: '完了する' }));
  await confirmPreview();
};

const unlinkedPreview = () =>
  mockedPreview.mockResolvedValue(
    pointsPreview({ use_points: 0, member_linked: false, usage_unit: 100, grant_points: 50 })
  );

describe('受注の完了画面', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockQuery = LIST_QUERY;
    mockedPreview.mockResolvedValue(
      pointsPreview({
        use_points: 0,
        member_linked: true,
        point_balance: 500,
        usage_unit: 100,
        grant_points: 50,
      })
    );
    mockedComplete.mockResolvedValue(confirmedOrder);
    mockedGet.mockImplementation(async (id: string) => ({ ...confirmedOrder, id }));
  });

  it('見出しに営業日と顧客を出し、戻るは一覧の検索条件を持って帰る', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: '受注の完了' })).toBeVisible();
    expect(await screen.findByText('2026-08-10 / 山田太郎')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: '戻る' })).toHaveAttribute('href', LIST_HREF);
  });

  it('既存の内訳を詳細の読み口から播く', async () => {
    // 詳細から播かないと、明細のある受注が空行で開き、そのまま完了すると既存の内訳を上書きして失う
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      course: { ...confirmedOrder.course, name: '90 分コース' },
      fee_lines: [
        {
          kind: 'BASE_COURSE',
          name: '90 分コース',
          amount: 18000,
          remuneration: 0,
          system_owned: false,
        },
        {
          kind: 'CREDIT_SURCHARGE',
          name: '指名',
          amount: 2000,
          remuneration: 0,
          system_owned: false,
        },
        {
          kind: 'POINT_REDEMPTION',
          name: 'ポイント利用',
          amount: 500,
          remuneration: 0,
          system_owned: true,
        },
      ],
    });
    renderPage();

    expect(await screen.findByText(/基本コース.*90 分コース/)).toBeInTheDocument();
    expect(screen.queryByLabelText('コース名')).not.toBeInTheDocument();
    expect(screen.getByLabelText('明細1の名称')).toHaveValue('指名');
    expect(screen.getByText(/ポイント利用.*-¥500/)).toBeInTheDocument();
    expect(screen.queryByLabelText('明細3の名称')).not.toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledWith('o1');
  });

  it('播いた内訳の総和で初回の見込みを取る', async () => {
    // 確定値の初期値を 0 のままにすると、明細のある受注の付与予定が最初の 1 画面だけ 0 で出る
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      fee_lines: [
        {
          kind: 'CREDIT_SURCHARGE',
          name: '指名',
          amount: 12000,
          remuneration: 0,
          system_owned: false,
        },
      ],
    });
    renderPage();

    await waitFor(() =>
      expect(mockedPreview).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({
          expected_version: 3,
          fee_lines: [expect.objectContaining({ amount: 12000 })],
        })
      )
    );
    expect(mockedPreview).not.toHaveBeenCalledWith(
      'o1',
      expect.objectContaining({ fee_lines: [expect.objectContaining({ amount: 0 })] })
    );
  });

  it('採用済みコースは完了画面で編集できない', async () => {
    renderPage();
    await screen.findByLabelText('明細1の名称');
    expect(screen.queryByLabelText('コース名')).not.toBeInTheDocument();
    await completeWith('8000');
    await waitFor(() => expect(mockedComplete).toHaveBeenCalled());
    expect(mockedComplete.mock.calls[0][1]).not.toHaveProperty('course_id');
  });

  it('受注を取得できなければ、その領域が失敗を名乗り再試行を出す', async () => {
    mockedGet.mockRejectedValueOnce(new Error('boom'));
    renderPage();

    expect(await screen.findByText('受注を取得できませんでした。')).toBeInTheDocument();
    expect(screen.queryByLabelText('明細1の金額')).not.toBeInTheDocument();

    mockedGet.mockResolvedValueOnce({
      ...confirmedOrder,
      fee_lines: [
        {
          kind: 'CREDIT_SURCHARGE',
          name: '再取得できた明細',
          amount: 3000,
          remuneration: 0,
          system_owned: false,
        },
      ],
    });
    fireEvent.click(screen.getByRole('button', { name: '再試行' }));

    expect(await screen.findByDisplayValue('再取得できた明細')).toBeInTheDocument();
  });

  it('受注が見つからなければ再試行を出さず、検索条件付きの一覧へ帰す', async () => {
    mockedGet.mockRejectedValueOnce({ response: { status: 404, data: {} } });
    renderPage();

    expect(await screen.findByText('受注が見つかりません。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'オーダー一覧へ' })).toHaveAttribute('href', LIST_HREF);
  });

  it('完了・取消済みの受注はフォームを出さず、一覧へ帰す', async () => {
    // 終端の受注に完了は成立しない。フォームを出すと、送っても必ず撥ねられる入力を勧めることになる
    mockedGet.mockResolvedValue({ ...confirmedOrder, status: 'COMPLETED' });
    renderPage();

    expect(
      await screen.findByText('この受注は完了または取消済みのため、完了できません。')
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('明細1の金額')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'オーダー一覧へ' })).toHaveAttribute('href', LIST_HREF);
    expect(mockedPreview).not.toHaveBeenCalled();
  });

  it('顧客未設定の受注は録入された連絡先で見出しに出す', async () => {
    // 会計の相手が誰か分からないまま完了させないため、台帳に着かなかった受注でも呼び名を出す
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      customer_name: undefined,
      contact_snapshot: { name: '重複照合の来客' },
    });
    renderPage();

    expect(await screen.findByText(/重複照合の来客（顧客未設定）/)).toBeInTheDocument();
  });

  it('開いた時点の事前計算は保存済みの明細と版で取る', async () => {
    renderPage();

    await waitFor(() =>
      expect(mockedPreview).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({
          expected_version: 3,
          fee_lines: [expect.objectContaining({ amount: 0 })],
        })
      )
    );
  });

  it('会計金額を確定すると、その金額で見込みを取り直す', async () => {
    // 打鍵ごとに取りに行かないので、欄を離れるまでは前の金額の見込みのまま
    renderPage();
    const input = await screen.findByLabelText('明細1の金額');
    fireEvent.change(input, { target: { value: '8000' } });
    fireEvent.blur(input);

    await waitFor(() =>
      expect(mockedPreview).toHaveBeenLastCalledWith(
        'o1',
        expect.objectContaining({
          expected_version: 3,
          fee_lines: [expect.objectContaining({ amount: 8000 })],
        })
      )
    );
  });

  it('未紐づけの受注では利用ポイント欄を出さず、未紐づけと名乗る', async () => {
    // 非会員に台帳は存在しない。欄を出すと、必ず失敗する入力を勧めることになる
    unlinkedPreview();
    renderPage();

    const points = await screen.findByRole('region', { name: 'ポイント' });
    expect(await within(points).findByText('未紐づけ')).toBeInTheDocument();
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();
    expect(within(points).queryByText('残高')).not.toBeInTheDocument();
    // 非会員には付与もされない。予定だけ出すと、完了しても増えないポイントを約束することになる
    expect(within(points).queryByText('付与予定')).not.toBeInTheDocument();
  });

  it('紐づけ済みの受注では残高・付与予定・利用単位を一つの節に出し、利用ポイントを受け付ける', async () => {
    renderPage();

    const points = await screen.findByRole('region', { name: 'ポイント' });
    expect(await within(points).findByText('紐づけ済み')).toBeInTheDocument();
    expect(within(points).getByText('500 ポイント')).toBeInTheDocument();
    expect(within(points).getByText('50 ポイント')).toBeInTheDocument();
    expect(within(points).getByText('100 ポイント単位')).toBeInTheDocument();
    expect(within(points).getByLabelText('利用ポイント')).toBeInTheDocument();
  });

  it('会計金額が空欄なら送信せず、無反応にもせず理由を出す', async () => {
    // 空欄は NaN であって null でも空文字でもないため、required だけでは素通りする
    renderPage();

    fireEvent.change(await screen.findByLabelText('明細1の金額'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();

    expect(await screen.findByText('金額を入力してください')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('会計金額が小数なら送信せず理由を出す', async () => {
    // noValidate は type="number" の暗黙の step=1 まで止める
    renderPage();

    await completeWith('1500.5');

    expect(await screen.findByText('金額は整数で入力してください')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが単位に合わなければ送信せず理由を出す', async () => {
    renderPage();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '150' } });
    await completeWith('8000');

    expect(
      await screen.findByText('利用ポイントは 100 ポイント単位で指定してください')
    ).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが残高を超えたら送信せず理由を出す', async () => {
    renderPage();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '600' } });
    await completeWith('8000');

    expect(await screen.findByText('残高を超えています（残高: 500）')).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが会計金額を超えたら送信せず理由を出す', async () => {
    // 請求より大きい割引に相当する利用を台帳へ積ませない。残高では引っ掛からない額で確かめる
    mockedPreview.mockResolvedValue(
      pointsPreview({
        use_points: 0,
        member_linked: true,
        point_balance: 50000,
        usage_unit: 100,
        grant_points: 10,
      })
    );
    renderPage();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '14000' } });
    await completeWith('1000');

    expect(
      await screen.findByText('会計金額を超えています（会計金額: 13000）')
    ).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('利用ポイントが空欄なら利用の項目ごと送らず、完了後は履歴を置き換えて検索条件付きの一覧へ帰る', async () => {
    // サーバ側は @Min(1)。0 を送ると撥ねられるので、利用しない完了ではキーごと落とす
    renderPage();
    await screen.findByLabelText('利用ポイント');

    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][0]).toBe('o1');
    expect(mockedComplete.mock.calls[0][1].fee_lines).toEqual([
      { kind: 'CREDIT_SURCHARGE', name: '会計', amount: 8000 },
    ]);
    expect(mockedComplete.mock.calls[0][1].use_points).toBeUndefined();
    expect(notify.success).toHaveBeenCalledWith('オーダーを完了しました');
    // 戻るで完了済みの受注の画面へ帰らないよう、push ではなく replace
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith(LIST_HREF));
    expect(screen.queryByLabelText('伝票QR')).not.toBeInTheDocument();
  });

  it('顧客ロックの競合は入力とサーバの案内を保持し、再送できる', async () => {
    const message = '顧客情報が変更中です。しばらく待ってから再試算してください';
    mockedComplete.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { error: message, details: { customer_lock: '入力を保持して再試算してください' } },
      },
    });
    renderPage();
    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    await completeWith('8000');
    await waitFor(() => expect(notify.error).toHaveBeenCalledWith(message));
    expect(screen.getByLabelText('利用ポイント')).toHaveValue(200);
    expect(screen.getByLabelText('明細1の金額')).toHaveValue(8000);
    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(notify.warning).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: '完了する' }));
    await confirmPreview();
    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(2));
    expect(mockedComplete.mock.calls[1][1]).toEqual(mockedComplete.mock.calls[0][1]);
  });

  it('試算中の顧客ロック競合は受注を読み直さず同じ入力で再試行できる', async () => {
    renderPage();
    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    fireEvent.change(screen.getByLabelText('明細1の金額'), { target: { value: '8000' } });
    const message = '顧客情報が変更中です。しばらく待ってから再試算してください';
    mockedPreview.mockRejectedValue({
      response: {
        status: 409,
        data: { error: message, details: { customer_lock: '入力を保持して再試算してください' } },
      },
    });
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    const dialog = await screen.findByRole('dialog', { name: '採用条件の確認' });
    expect(await within(dialog).findByText(message)).toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(mockedComplete).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: '最新の受注を読み直す' })).not.toBeInTheDocument();
    mockedPreview.mockResolvedValue(
      pointsPreview({ member_linked: true, use_points: 200, usage_unit: 100, grant_points: 50 })
    );
    fireEvent.click(within(dialog).getByRole('button', { name: '再試行' }));
    await confirmPreview();
    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        use_points: 200,
        fee_lines: [{ kind: 'CREDIT_SURCHARGE', name: '会計', amount: 8000 }],
      })
    );
    expect(mockedGet).toHaveBeenCalledTimes(1);
  });

  it('保存時の残高不足は理由と利用点数の見直しを示し入力を保持する', async () => {
    mockedComplete.mockRejectedValueOnce({
      response: {
        status: 409,
        data: {
          error: 'ポイント残高が不足しています（残高: 100）',
          details: { use_points: '利用点数を見直してください' },
        },
      },
    });
    renderPage();
    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    await completeWith('8000');
    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith(
        expect.stringMatching(/残高: 100.*利用点数を見直してください/)
      )
    );
    expect(screen.getByLabelText('利用ポイント')).toHaveValue(200);
    expect(mockedComplete).toHaveBeenCalledTimes(1);
    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('利用ポイントを入れたら、その値を添えて送る', async () => {
    renderPage();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    await completeWith('8000');

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1]).toEqual({
      expected_version: 3,
      confirmation_token: 'confirmed',
      fee_lines: [{ kind: 'CREDIT_SURCHARGE', name: '会計', amount: 8000 }],
      use_points: 200,
    });
  });

  it('会員資格が変わったら利用入力を再試算し、資格エラーでは保存しない', async () => {
    renderPage();

    fireEvent.change(await screen.findByLabelText('利用ポイント'), { target: { value: '200' } });
    unlinkedPreview();
    fireEvent.change(screen.getByLabelText('明細1の名称'), { target: { value: '会計' } });
    const input = screen.getByLabelText('明細1の金額');
    fireEvent.change(input, { target: { value: '8000' } });
    fireEvent.blur(input);

    await screen.findByText('未紐づけ');
    expect(screen.getByLabelText('利用ポイント')).toHaveValue(200);
    mockedPreview.mockRejectedValue(new Error('資格変更'));
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();

    expect(
      await screen.findByText('試算できませんでした。入力と権限を確認してください。')
    ).toBeInTheDocument();
    expect(mockedPreview).toHaveBeenLastCalledWith(
      'o1',
      expect.objectContaining({ use_points: 200 })
    );
    expect(mockedComplete).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '入力に戻る' }));
    fireEvent.change(screen.getByLabelText('利用ポイント'), { target: { value: '0' } });
    mockedPreview.mockResolvedValue(
      pointsPreview({ use_points: 0, member_linked: false, usage_unit: 100, grant_points: 0 })
    );
    fireEvent.click(await screen.findByRole('button', { name: '完了する' }));
    await confirmPreview();
    await waitFor(() =>
      expect(mockedComplete).toHaveBeenCalledWith(
        'o1',
        expect.objectContaining({ use_points: undefined })
      )
    );
  });

  it('内訳を播いた詳細そのものの版を完了と一緒に送る', async () => {
    // 送る内訳と名指す版は同じ瞬間の姿でなければならない。別々に採ると、照合が通ったのに
    // 古い内訳で凍らせる完了が成立する
    mockedGet.mockResolvedValue({
      ...confirmedOrder,
      version: 7,
      fee_lines: [
        {
          kind: 'CREDIT_SURCHARGE',
          name: '指名',
          amount: 12000,
          remuneration: 0,
          system_owned: false,
        },
      ],
    });
    renderPage();

    await screen.findByLabelText('明細1の名称');
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();

    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(1));
    expect(mockedComplete.mock.calls[0][1].expected_version).toBe(7);
  });

  it('版の食い違いは入力置換を警告し、取り直した内容と版で播き直す', async () => {
    // 取り直さないと画面は古い版を持ったままで、その場の再送は何度でも 409 になる（行き止まり）
    mockedGet
      .mockResolvedValueOnce({
        ...confirmedOrder,
        version: 3,
        fee_lines: [
          {
            kind: 'CREDIT_SURCHARGE',
            name: '古い明細',
            amount: 1000,
            remuneration: 0,
            system_owned: false,
          },
        ],
      })
      .mockResolvedValueOnce({
        ...confirmedOrder,
        version: 4,
        fee_lines: [
          {
            kind: 'CREDIT_SURCHARGE',
            name: '他の操作者が直した明細',
            amount: 5000,
            remuneration: 0,
            system_owned: false,
          },
        ],
      });
    mockedComplete.mockRejectedValueOnce(conflict);
    renderPage();

    expect(await screen.findByLabelText('明細1の名称')).toHaveValue('古い明細');
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();

    await waitFor(() =>
      expect(notify.warning).toHaveBeenCalledWith(
        '他の操作者が更新しました。入力を最新の内容に置き換えました'
      )
    );
    expect(notify.success).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.getByLabelText('明細1の名称')).toHaveValue('他の操作者が直した明細')
    );
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();
    await waitFor(() => expect(mockedComplete).toHaveBeenCalledTimes(2));
    expect(mockedComplete.mock.calls[1][1].expected_version).toBe(4);
  });

  it('取り直した受注が終端なら、フォームを畳んで終端を名乗り、一覧へ帰す', async () => {
    // 終端の受注を播き直すと、一致する版で再送できて今度は 400 の行き止まりに変わる
    mockedGet
      .mockResolvedValueOnce({
        ...confirmedOrder,
        fee_lines: [
          {
            kind: 'CREDIT_SURCHARGE',
            name: '古い明細',
            amount: 1000,
            remuneration: 0,
            system_owned: false,
          },
        ],
      })
      .mockResolvedValueOnce({ ...confirmedOrder, status: 'COMPLETED', version: 4 });
    mockedComplete.mockRejectedValueOnce(conflict);
    renderPage();

    expect(await screen.findByLabelText('明細1の名称')).toHaveValue('古い明細');
    fireEvent.click(screen.getByRole('button', { name: '完了する' }));
    await confirmPreview();

    expect(
      await screen.findByText('この受注は完了または取消済みのため、完了できません。')
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('明細1の名称')).not.toBeInTheDocument();
    // 文言は領域の名乗りだけ。入力置換の警告まで重ねると、従うべき指示が 2 つ同時に出る
    expect(notify.warning).not.toHaveBeenCalled();
    // 完了の成功ではない。成功の通知を出すと「オーダーを完了しました」が嘘になる
    expect(notify.success).not.toHaveBeenCalled();
    expect(mockedComplete).toHaveBeenCalledTimes(1);
  });

  it('完了に失敗したら、対処方法を含むサーバの文言をそのまま出す', async () => {
    mockedComplete.mockRejectedValue({
      response: { status: 400, data: { error: 'ポイント残高が不足しています（残高: 500）' } },
    });
    renderPage();

    await completeWith('8000');

    await waitFor(() =>
      expect(notify.error).toHaveBeenCalledWith('ポイント残高が不足しています（残高: 500）')
    );
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('事前計算に失敗したら領域が自分で名乗り、通知には出さず、再試算できるまで保存しない', async () => {
    // 見込みが読めないことは入力の誤りではない。単位も残高もサーバ側が再検証するので、
    // ここで送信を塞ぐと読み込みの失敗が会計そのものを止めてしまう
    mockedPreview.mockRejectedValue(new Error('boom'));
    renderPage();

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('ポイントの見込みを取得できませんでした')).toBeInTheDocument();
    expect(notify.error).not.toHaveBeenCalled();
    // 会員かどうかが分からない以上、利用ポイントは受け付けない
    expect(screen.queryByLabelText('利用ポイント')).not.toBeInTheDocument();

    await completeWith('8000');

    expect(
      await screen.findByText('試算できませんでした。入力と権限を確認してください。')
    ).toBeInTheDocument();
    expect(mockedComplete).not.toHaveBeenCalled();
  });

  it('見込みの再試行は同じ会計金額のまま取り直す', async () => {
    mockedPreview.mockRejectedValueOnce(new Error('boom'));
    renderPage();

    const region = await screen.findByRole('alert');
    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('紐づけ済み')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('伝票トークンが返ったら画面の上に QR を出し、閉じるまで一覧へ遷移しない', async () => {
    // 生値は完了応答にしか現れない。ここで遷移すると、客が後から来店を取り戻す手段ごと消える
    unlinkedPreview();
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    renderPage();

    await completeWith('8000');

    const dialog = await screen.findByRole('dialog', { name: '伝票QRコード' });
    // 客が読み取って行き着くのは申領画面。トークンだけを載せると、読み取っても開く先が無い
    expect(within(dialog).getByTestId('qr')).toHaveAttribute(
      'data-value',
      'http://kizuna.test/member/receipts#raw-receipt-token'
    );
    expect(within(dialog).getByText('2026-08-10 / 山田太郎')).toBeInTheDocument();
    expect(notify.success).toHaveBeenCalledWith('オーダーを完了しました');
    expect(mockReplace).not.toHaveBeenCalled();
    // 閉じる手段は操作帯のボタンだけ
    expect(within(dialog).queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: '閉じて一覧へ' }));
    expect(mockReplace).toHaveBeenCalledWith(LIST_HREF);
  });

  it('QR を出している間は Escape で閉じない', async () => {
    unlinkedPreview();
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    renderPage();

    await completeWith('8000');
    await screen.findByLabelText('伝票QR');
    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });

    expect(screen.getByLabelText('伝票QR')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('QR を出した後に開き直しても（再読込）、前に発行した QR は出さない', async () => {
    // 生値は発行の応答にしか現れない。出し直せると「今だけ」と書いた表示が嘘になる
    unlinkedPreview();
    mockedComplete.mockResolvedValue({ ...confirmedOrder, receipt_token: 'raw-receipt-token' });
    const { unmount } = renderPage();
    await completeWith('8000');
    await screen.findByLabelText('伝票QR');
    unmount();

    mockedGet.mockResolvedValue({ ...confirmedOrder, status: 'COMPLETED' });
    renderPage();

    expect(
      await screen.findByText('この受注は完了または取消済みのため、完了できません。')
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('伝票QR')).not.toBeInTheDocument();
  });

  it('送信中は完了も戻るも押せない', async () => {
    let settle: (order: Order) => void = () => {};
    mockedComplete.mockReturnValueOnce(
      new Promise(resolve => {
        settle = resolve;
      })
    );
    renderPage();

    await completeWith('8000');

    await waitFor(() => expect(screen.getByRole('button', { name: '処理中...' })).toBeDisabled());
    // 送信中に離れると、応答にしか現れない伝票トークンを受け取れずに失う
    expect(screen.queryByRole('link', { name: '戻る' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '戻る' })).toBeDisabled();

    await act(async () => settle(confirmedOrder));
  });
});
