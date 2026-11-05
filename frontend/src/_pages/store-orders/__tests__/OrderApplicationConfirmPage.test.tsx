import { useMe } from '@/entities/user';
import type { PermissionCode } from '@/entities/user';
import { chooseCourse, confirmPreview } from '../lib/orderTestSupport';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import OrderApplicationConfirmPage from '../ui/OrderApplicationConfirmPage';
import { OrderApplicationDetail, orderApi, orderApplicationApi } from '@/entities/order';

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  useStoreContext: () => ({
    stores: [{ id: 1, name: 'テスト店舗' }],
    currentStoreId: '1',
    currentStore: { id: 1, name: 'テスト店舗' },
    loadFailed: false,
    reload: jest.fn(),
    switchStore: jest.fn(),
  }),
}));
jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const meWith = (permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'staff@example.com',
      user_type: 'STAFF',
      permissions,
      line_linked: false,
      email_verified: false,
    },
    isLoading: false,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
const allPermissions: PermissionCode[] = ['ORDER_MANAGE', 'CUSTOMER_MANAGE'];

jest.mock('@/entities/order', () => ({
  ...jest.requireActual('@/entities/order'),
  orderApi: {
    ...jest.requireActual('../lib/orderTestSupport').courseApiMocks(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
  },
  orderApplicationApi: {
    ...jest.requireActual('../lib/orderTestSupport').courseApiMocks(),
    confirm: jest.fn(),
    detail: jest.fn(),
  },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockPush = jest.fn();
const mockReplace = jest.fn();
let mockParams: { id: string } = { id: 'app-1' };
let mockQuery = '';
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, back: jest.fn() }),
  useParams: () => mockParams,
  useSearchParams: () => new URLSearchParams(mockQuery),
}));

const mockedConfirm = orderApplicationApi.confirm as jest.Mock;
const mockedDetail = jest.mocked(orderApplicationApi.detail);
const mockedCustomerList = orderApi.customerCandidates as jest.Mock;

function guestApplication(overrides: Partial<OrderApplicationDetail> = {}): OrderApplicationDetail {
  return {
    id: 'app-1',
    business_date: '2026-08-25',
    pax: 2,
    status: 'PENDING',
    expired: false,
    contact_snapshot: { name: 'ゲスト花子', phone_number: '09000000000' },
    business_contact_permissions: [],
    contact_imports: [],
    ...overrides,
  };
}

function memberApplication(
  overrides: Partial<OrderApplicationDetail> = {}
): OrderApplicationDetail {
  return {
    id: 'app-2',
    business_date: '2026-08-25',
    pax: 2,
    status: 'PENDING',
    expired: false,
    requester_member_code: '123456789012',
    requester_declared_name: '名乗り太郎',
    business_contact_permissions: [],
    contact_imports: [],
    ...overrides,
  };
}

const renderPage = (application: OrderApplicationDetail) => {
  mockParams = { id: application.id ?? '' };
  mockedDetail.mockResolvedValue(application);
  return render(<OrderApplicationConfirmPage />);
};

const confirmButton = () => screen.getByRole('button', { name: '確定する' });

/** キーボード/クリックで開く経路のみを使う（ポインタ系 API は jsdom に無い）。 */
async function pickCustomerMode(optionName: string) {
  fireEvent.click(await screen.findByRole('combobox', { name: /顧客/ }));
  const option = await screen.findByRole('option', { name: optionName });
  // Base UI の Item は pointerdown を経ていない mouse click を無視する
  fireEvent.pointerDown(option);
  fireEvent.click(option);
}

beforeEach(() => {
  jest.clearAllMocks();
  mockQuery = '';
  meWith(allPermissions);
  jest.mocked(orderApi.listReceptionists).mockResolvedValue([]);
  mockedConfirm.mockResolvedValue({ id: 'o1' });
});

describe('予約申請の確定画面', () => {
  it('会員申請を確定し、確定端点の項目だけを送って検索条件付きの受注一覧へ戻る', async () => {
    mockQuery = 'customer_name=%E8%8A%B1%E5%AD%90&sort_key=PAX&desc=true';
    renderPage(
      memberApplication({
        arrival_scheduled_start_time: '19:30:00',
        cast_id: 'cast-1',
        cast_name: '担当花子',
        remarks: '申請の備考',
      })
    );
    expect(
      await screen.findByRole('heading', { level: 1, name: '予約申請を確定' })
    ).toBeInTheDocument();
    expect(await screen.findByLabelText('お客様名')).toHaveValue('名乗り太郎');
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    const [id, body] = mockedConfirm.mock.calls[0];
    expect(id).toBe('app-2');
    // 確定端点は未知の項目を 400 で拒む。受注作成だけの項目（受付経路・業務連絡の可否など）を載せない
    expect(Object.keys(JSON.parse(JSON.stringify(body))).sort()).toEqual(
      [
        'arrival_scheduled_start_time',
        'business_date',
        'cast_id',
        'confirmation_token',
        'contact_snapshot',
        'course_id',
        'fee_lines',
        'pax',
        'remarks',
        'special_service_ids',
      ].sort()
    );
    expect(body).toEqual(
      expect.objectContaining({
        business_date: '2026-08-25',
        arrival_scheduled_start_time: '19:30:00',
        pax: 2,
        cast_id: 'cast-1',
        course_id: 'course-1',
        remarks: '申請の備考',
        // 会員申請の顧客は会員の紐づけが決める
        customer_selection: undefined,
        contact_imports: undefined,
        confirmation_token: 'confirmed',
      })
    );
    expect(body.contact_snapshot.name).toBe('名乗り太郎');
    // 確定後の戻るで処理済みの申請の画面へ戻さない
    await waitFor(() =>
      expect(mockReplace).toHaveBeenCalledWith(
        '/store/orders?customer_name=%E8%8A%B1%E5%AD%90&sort_key=PAX&desc=true'
      )
    );
    expect(mockPush).not.toHaveBeenCalled();
  });

  it.each([
    ['営業日', '営業日を選択してください'],
    ['人数', '人数を入力してください'],
  ])('%s が空のままの確定は欄の傍で止める', async (label, message) => {
    renderPage(guestApplication());
    await chooseCourse();
    fireEvent.change(await screen.findByLabelText(label), { target: { value: '' } });
    fireEvent.click(confirmButton());
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(orderApplicationApi.previewConfirmation).not.toHaveBeenCalled();
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('申請が見つからなければフォームを出さず、検索条件付きの受注一覧へ戻る導線を出す', async () => {
    mockQuery = 'sort_key=PAX';
    mockParams = { id: 'app-1' };
    mockedDetail.mockRejectedValue({ response: { status: 404 } });
    render(<OrderApplicationConfirmPage />);
    expect(await screen.findByText('予約申請が見つかりません。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '確定する' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'オーダー一覧へ' })).toHaveAttribute(
      'href',
      '/store/orders?sort_key=PAX'
    );
  });

  it.each([
    ['確定済み', { status: 'CONFIRMED' as const }],
    ['失効', { expired: true }],
  ])('%s の申請はフォームを出さない', async (_label, overrides) => {
    renderPage(guestApplication(overrides));
    expect(
      await screen.findByText('この予約申請は処理済みか失効しているため確定できません。')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '確定する' })).not.toBeInTheDocument();
  });

  it('戻るは検索条件付きの受注一覧へのリンクで、受注作成だけの項目を出さない', async () => {
    mockQuery = 'business_date=2026-08-25';
    renderPage(guestApplication());
    expect(await screen.findByRole('link', { name: '戻る' })).toHaveAttribute(
      'href',
      '/store/orders?business_date=2026-08-25'
    );
    expect(screen.queryByLabelText('住所')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('キャスト・ドライバーへのメッセージ')).not.toBeInTheDocument();
    expect(screen.queryByText('受付経路')).not.toBeInTheDocument();
  });

  it('確定に失敗したら一覧へ遷移せず、サーバの文言を出す', async () => {
    mockedConfirm.mockRejectedValue({
      response: { status: 409, data: { error: '指名のキャストが出勤していません' } },
    });
    renderPage(guestApplication());
    await chooseCourse();
    fireEvent.click(await screen.findByRole('button', { name: '確定する' }));
    await confirmPreview();
    await waitFor(() =>
      expect(jest.requireMock('@/shared/notify').notify.error).toHaveBeenCalledWith(
        '指名のキャストが出勤していません'
      )
    );
    expect(mockReplace).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '確定する' })).toBeEnabled();
  });

  it('新規顧客への連絡先と販促同意の取り込みをそれぞれ明示する', async () => {
    const application = guestApplication({
      contact_consent: {
        version: '1',
        business_text: '業務',
        marketing_text: '販促',
        business_allowed: true,
        marketing_allowed: true,
        acquired_at: '2026-09-25T10:00:00Z',
      },
      business_contact_permissions: [
        { type: 'PHONE', value: '+819000000000', status: 'ALLOWED', decision: 'ALLOWED' },
      ],
    });
    renderPage(application);
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '取り込み先' },
    });
    fireEvent.click(await screen.findByRole('checkbox', { name: '電話を台帳へ取り込む' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '電話の販促同意を取り込む' }));
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() =>
      expect(mockedConfirm).toHaveBeenCalledWith(
        'app-1',
        expect.objectContaining({
          contact_imports: [{ type: 'PHONE', import_marketing_consent: true }],
        })
      )
    );
  });

  it('申請の同意原文と今回の拒否判定を表示する', async () => {
    const application = guestApplication({
      contact_consent: {
        version: '1',
        business_text: '申請時の業務文面',
        marketing_text: '申請時の販促文面',
        business_allowed: true,
        marketing_allowed: false,
        acquired_at: '2026-09-25T10:00:00Z',
      },
      business_contact_permissions: [
        { type: 'PHONE', value: '+819000000000', status: 'ALLOWED', decision: 'STORE_DENIED' },
      ],
    });
    renderPage(application);
    expect(await screen.findByText('申請時の業務文面')).toBeInTheDocument();
    expect(screen.getByText(/店舗内の拒否により連絡不可/)).toBeInTheDocument();
    expect(screen.getByText(/販促同意：未選択/)).toBeInTheDocument();
  });

  it('空の新規顧客名は入力へ焦点を戻し、形式不正のメールは欄の傍で説明する', async () => {
    renderPage(guestApplication());
    await pickCustomerMode('新規顧客');
    await chooseCourse();
    fireEvent.click(confirmButton());
    const name = await screen.findByLabelText('新規顧客名');
    await waitFor(() => expect(name).toHaveFocus());
    expect(name).toHaveAttribute('aria-invalid', 'true');
    fireEvent.change(name, { target: { value: '新規顧客' } });
    fireEvent.change(screen.getByLabelText('メール'), { target: { value: 'invalid' } });
    fireEvent.click(confirmButton());
    await screen.findByText('メールアドレスの形式が正しくありません');
    expect(mockedConfirm).not.toHaveBeenCalled();
  });

  it('顧客管理権限がなくても既存選択と連絡先入力ができ、新規登録は表示しない', async () => {
    meWith([]);
    renderPage(guestApplication());
    fireEvent.change(await screen.findByLabelText('メール'), {
      target: { value: 'once@example.com' },
    });
    fireEvent.click(await screen.findByRole('combobox', { name: '顧客の選択' }));
    expect(await screen.findByRole('option', { name: '既存顧客' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: '新規顧客' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('メール')).toHaveValue('once@example.com');
  });

  it('会員申請の空の連絡先からも名乗りを受注に写し、顧客選択は送らない', async () => {
    renderPage({
      ...memberApplication(),
      contact_snapshot: { name: null, phone_number: null, email: null, line_id: null },
    });
    expect(await screen.findByLabelText('お客様名')).toHaveValue('名乗り太郎');
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toBeUndefined();
    expect(mockedConfirm.mock.calls[0][1].contact_snapshot.name).toBe('名乗り太郎');
  });

  it.each([false, true])(
    '指名解除で特殊サービスを消し、解除を戻しても旧選択を送らない（戻す: %s）',
    async restore => {
      jest.mocked(orderApi.specialServiceCandidates).mockResolvedValue({
        rows: [
          {
            service_id: 'special-1',
            revision_id: 'sr1',
            revision_number: 1,
            terms_version: 1,
            name: '受諾済み追加',
            charge_type: 'PAID',
            price: 2000,
            remuneration: 1000,
            consent_event_id: 'consent-1',
            consent_version: 1,
          },
        ],
        page: 0,
        pageCount: 1,
        total: 1,
      });
      renderPage(guestApplication({ cast_id: 'cast-1', cast_name: '担当花子' }));
      await chooseCourse();
      const option = await screen.findByRole('checkbox', { name: /受諾済み追加/ });
      fireEvent.click(option);
      expect(option).toBeChecked();
      expect(screen.getByText('小計 ¥14,000')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('checkbox', { name: '指名を外して確定する' }));
      expect(screen.queryByRole('checkbox', { name: /受諾済み追加/ })).not.toBeInTheDocument();
      expect(
        screen.getByText('指名を外すため、特殊サービスは選択できません。')
      ).toBeInTheDocument();
      expect(screen.getByText('小計 ¥12,000')).toBeInTheDocument();
      if (restore) {
        fireEvent.click(screen.getByRole('checkbox', { name: '指名を外して確定する' }));
        expect(await screen.findByRole('checkbox', { name: /受諾済み追加/ })).not.toBeChecked();
      }
      fireEvent.click(confirmButton());
      await confirmPreview();
      await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
      expect(jest.mocked(orderApplicationApi.previewConfirmation).mock.calls.at(-1)?.[1]).toEqual(
        expect.objectContaining({
          special_service_ids: [],
          cast_id: restore ? 'cast-1' : undefined,
        })
      );
      expect(mockedConfirm.mock.calls[0][1]).toEqual(
        expect.objectContaining({
          special_service_ids: [],
          cast_id: restore ? 'cast-1' : undefined,
        })
      );
    }
  );

  it('受付の明示選択は数値 ID で確定する', async () => {
    jest
      .mocked(orderApi.listReceptionists)
      .mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    renderPage(guestApplication());
    fireEvent.click(await screen.findByRole('combobox', { name: '受付担当' }));
    const item = await screen.findByRole('option', { name: '受付花子' });
    fireEvent.pointerDown(item);
    fireEvent.click(item);
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();
    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].receptionist_id).toBe(7);
  });

  it('ゲスト申請では顧客の決め方を店員に選ばせる', async () => {
    renderPage(guestApplication());

    expect(await screen.findByText('顧客の選択')).toBeInTheDocument();
  });

  it('会員申請では顧客を選ばせない（顧客は会員の紐づけが決める）', async () => {
    renderPage(memberApplication());

    expect(await screen.findByText('予約申請を確定')).toBeInTheDocument();
    expect(screen.queryByText('顧客の選択')).not.toBeInTheDocument();
  });

  it('新規作成を選ぶと申請の連絡先が事前入力されている', async () => {
    renderPage(guestApplication());
    await screen.findByText('顧客の選択');

    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });

    expect(await screen.findByLabelText('お客様名')).toHaveValue('ゲスト花子');
    expect(screen.getByLabelText('電話番号')).toHaveValue('09000000000');
  });

  it('既定は顧客未設定で、顧客の項目を送らずに確定する', async () => {
    renderPage(guestApplication());
    await screen.findByText('顧客の選択');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    // 既定で台帳行を起こすと、店員が判断しないまま重複した行が積み上がる
    expect(mockedConfirm.mock.calls[0][1].receptionist_id).toBeUndefined();
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({ mode: 'NONE' });
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });

  it('新規作成を選んだ確定は new_customer を送る', async () => {
    renderPage(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });
    await screen.findByLabelText('お客様名');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'NEW',
      new_customer: { name: '台帳の名前' },
    });
  });

  it('既存顧客は店員が探して選んだ 1 行だけを送る（電話番号の自動照合はしない）', async () => {
    mockedCustomerList.mockResolvedValue({
      rows: [{ id: 'cust-1', name: 'ゲスト花子', phone_number: '09000000000' }],
      page: 0,
      pageCount: 1,
      total: 0,
    });
    renderPage(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('既存顧客');

    fireEvent.click(await screen.findByRole('combobox', { name: '既存顧客の検索' }));
    const candidate = await screen.findByRole('option', { name: /ゲスト花子/ });
    fireEvent.pointerDown(candidate);
    fireEvent.click(candidate);
    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'EXISTING',
      customer_id: 'cust-1',
    });
    expect(mockedConfirm.mock.calls[0][1].new_customer).toBeUndefined();
  });

  it('ゲスト申請の確定でも、顧客の決め方と一緒に選択したコースを運ぶ', async () => {
    // 確定は受注の出生なので、快照が写る経路は会員申請とゲスト申請で分かれない。
    // 顧客の決め方（ゲスト専用）と快照（両方）が同じ要求に同居することを固定する
    renderPage(guestApplication());
    await screen.findByText('顧客の選択');
    await pickCustomerMode('新規顧客');
    fireEvent.change(await screen.findByLabelText('新規顧客名'), {
      target: { value: '台帳の名前' },
    });
    await screen.findByLabelText('お客様名');

    await chooseCourse();
    fireEvent.click(confirmButton());
    await confirmPreview();

    await waitFor(() => expect(mockedConfirm).toHaveBeenCalled());
    expect(mockedConfirm.mock.calls[0][1].course_id).toBe('course-1');
    expect(mockedConfirm.mock.calls[0][1].customer_selection).toEqual({
      mode: 'NEW',
      new_customer: { name: '台帳の名前' },
    });
  });
});
