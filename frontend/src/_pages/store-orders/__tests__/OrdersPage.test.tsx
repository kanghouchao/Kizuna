import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import OrderListPage from '../ui/OrdersPage';
import CreateOrderPage from '../ui/OrderCreatePage';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  // 表示ラベル等の定数は本物を使う（API だけを差し替える）
  ...jest.requireActual('@/entities/order/model/types'),
  orderApi: {
    list: jest.fn(),
    create: jest.fn(),
    listReceptionists: jest.fn(),
    listCastCandidates: jest.fn(),
    listReservationRequests: jest.fn(),
    confirm: jest.fn(),
    decline: jest.fn(),
    complete: jest.fn(),
    completionPreview: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedOrderApi = orderApi as jest.Mocked<typeof orderApi>;

describe('店側オーダー画面の描画', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 一覧ページは予約受付 inbox を同居させるため、その読み口も満たしておく
    mockedOrderApi.listReservationRequests.mockResolvedValue({
      rows: [],
      nextCursor: null,
    });
  });

  // fixture は手書きであり、バックエンドの実応答を見ているわけではない。
  // 機械的な担保は fixture と Order 型の照合だけで、それは tsc の側で効く（jest は型検査しない）。
  it('一覧は各行の項目を表示すること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [
        {
          id: '1',
          business_date: '2026-07-03',
          customer_name: '山田太郎',
          cast_name: '花子',
          course_minutes: 60,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          status: 'CREATED',
        },
      ],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<OrderListPage />);

    expect(await screen.findByText('2026-07-03')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('花子')).toBeInTheDocument();
    expect(screen.getByText('60 分')).toBeInTheDocument();
  });

  it('予約受付カードの見出しが見出しナビゲーションに乗ること', async () => {
    // CardTitle は div を描くため、role と aria-level の両方が無いと支援技術の見出し
    // 一覧から消える。描画差分には現れない欠落なので、ここで固定する。
    mockedOrderApi.list.mockResolvedValue({ rows: [], page: 0, pageCount: 1, total: 0 });

    render(<OrderListPage />);

    expect(await screen.findByRole('heading', { level: 2, name: '予約受付' })).toBeInTheDocument();
  });

  it('一覧の遷移リンクは店舗スコープのパスを指すこと', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [
        {
          id: '7',
          business_date: '2026-07-03',
          customer_name: '山田太郎',
          cast_name: '花子',
          course_minutes: 60,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          status: 'CREATED',
        },
      ],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<OrderListPage />);
    await screen.findByText('2026-07-03');

    expect(screen.getByRole('link', { name: /新規オーダー登録/ })).toHaveAttribute(
      'href',
      '/store/1/orders/create'
    );
    const links = screen.getAllByRole('link');
    expect(links.map(link => link.getAttribute('href'))).toContain('/store/1/orders/7/edit');
  });

  it('店舗が手入力した未確定の受注を「申請中」と表示しないこと', async () => {
    // CREATED は申請でも店舗起点の受注でも起きる。申請中と呼ぶと、店舗が起こした受注まで
    // 会員の申請に見えて予約受付 inbox の対象と誤認される。
    mockedOrderApi.list.mockResolvedValue({
      rows: [{ id: '9', business_date: '2026-07-05', status: 'CREATED' }],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<OrderListPage />);

    expect(await screen.findByText('未確定')).toBeInTheDocument();
    expect(screen.queryByText('申請中')).not.toBeInTheDocument();
    expect(screen.queryByText('WEB申請')).not.toBeInTheDocument();
  });

  it('キャスト未指名はフリー表記になること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [
        {
          id: '8',
          business_date: '2026-07-04',
          customer_name: '鈴木花子',
          // 未指名はキーごと応答から消える（null は来ない）
          course_minutes: 90,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          status: 'CREATED',
        },
      ],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<OrderListPage />);

    expect(await screen.findByText('フリー')).toBeInTheDocument();
  });

  it('オーダーが0件なら不在メッセージを表示すること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [],
      page: 0,
      pageCount: 0,
      total: 0,
    });

    render(<OrderListPage />);

    expect(await screen.findByText('オーダーがありません')).toBeInTheDocument();
  });

  it('新規登録はバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedOrderApi.create.mockResolvedValue({});
    mockedOrderApi.listReceptionists.mockResolvedValue([{ id: 7, display_name: '受付花子' }]);
    mockedOrderApi.listCastCandidates.mockResolvedValue([{ id: 'cast-1', name: '花子' }]);

    render(<CreateOrderPage />);
    // 受付・キャストはサーバ側が @NotNull / @NotBlank。選ばないと送信自体が止まる。
    // キャストを先に選ぶ（指名の選択が開いている間は受付の選択を開けない）。
    fireEvent.click(await screen.findByRole('combobox', { name: /キャスト/ }));
    const option = await screen.findByRole('option', { name: /花子/ });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option);
    fireEvent.click(option);
    fireEvent.click(await screen.findByRole('combobox', { name: /受付(?!経路)/ }));
    const option2 = await screen.findByRole('option', { name: '受付花子' });
    // Base UI の Item は pointerdown を経ていない mouse click を無視する
    fireEvent.pointerDown(option2);
    fireEvent.click(option2);
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    const body = mockedOrderApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('business_date');
    expect(body).toHaveProperty('course_minutes', 60);
    // セレクト未操作時の既定ペイロード（挙動維持の錨）
    expect(body).toHaveProperty('classification', 'ーー');
    expect(body).toHaveProperty('has_pet', false);
    expect(body).toHaveProperty('discount_name', '');
    // 受付は Java 側が Long。文字列ではなく数値で送る。
    expect(body).toHaveProperty('receptionist_id', 7);
    expect(body).toHaveProperty('cast_id', 'cast-1');
    expect(body).not.toHaveProperty('store_name');
    expect(body).not.toHaveProperty('storeName');
    expect(body).not.toHaveProperty('businessDate');
  });
});

describe('オーダー一覧の完了処理', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.listReservationRequests.mockResolvedValue({ rows: [], nextCursor: null });
    mockedOrderApi.completionPreview.mockResolvedValue({
      member_linked: false,
      usage_unit: 100,
      grant_points: 0,
    });
  });

  it('完了は確定済みの行にだけ出ること', async () => {
    // 確定済み以外への完了はサーバ側が撥ねる。押せる形で出すと必ず失敗する操作を勧めることになる
    mockedOrderApi.list.mockResolvedValue({
      rows: [
        { id: 'a', business_date: '2026-08-01', status: 'CONFIRMED' },
        { id: 'b', business_date: '2026-08-02', status: 'CREATED' },
        { id: 'c', business_date: '2026-08-03', status: 'COMPLETED' },
        { id: 'd', business_date: '2026-08-04', status: 'CANCELLED' },
      ],
      page: 0,
      pageCount: 1,
      total: 4,
    });

    render(<OrderListPage />);
    await screen.findByText('2026-08-01');

    expect(screen.getAllByRole('button', { name: '完了' })).toHaveLength(1);
  });

  it('完了を押すと、その受注の完了処理モーダルが開くこと', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [{ id: 'a', business_date: '2026-08-01', status: 'CONFIRMED', total_fee: 0 }],
      page: 0,
      pageCount: 1,
      total: 1,
    });

    render(<OrderListPage />);
    fireEvent.click(await screen.findByRole('button', { name: '完了' }));

    expect(await screen.findByText('完了処理')).toBeInTheDocument();
    await waitFor(() => expect(mockedOrderApi.completionPreview).toHaveBeenCalledWith('a', 0));
  });

  it('状態バッジは状態ごとに塗り分けること', async () => {
    // 一律で成功の緑に塗ると、キャンセルまで正常終了に見える
    mockedOrderApi.list.mockResolvedValue({
      rows: [
        { id: 'a', business_date: '2026-08-01', status: 'CONFIRMED' },
        { id: 'd', business_date: '2026-08-04', status: 'CANCELLED' },
      ],
      page: 0,
      pageCount: 1,
      total: 2,
    });

    render(<OrderListPage />);

    expect(await screen.findByText('確定')).toHaveClass('bg-success/10');
    const cancelled = screen.getByText('キャンセル');
    expect(cancelled).toHaveClass('bg-destructive/10');
    expect(cancelled).not.toHaveClass('bg-success/10');
  });
});

describe('オーダー一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 一覧ページは予約受付 inbox を同居させるため、その読み口も満たしておく
    mockedOrderApi.listReservationRequests.mockResolvedValue({
      rows: [],
      nextCursor: null,
    });
    mockedOrderApi.list.mockResolvedValue({
      rows: [],
      page: 0,
      pageCount: 0,
      total: 0,
    });
  });

  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    render(<OrderListPage />);
    await screen.findByText('オーダーがありません');

    // e2e（hybrid-console-access）は見出し名 'オーダー一覧' の完全一致で到達確認する
    expect(screen.getByRole('heading', { level: 1, name: 'オーダー一覧' })).toBeInTheDocument();
    expect(screen.getByText('当日の注文状況を確認・管理できます。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規オーダー登録' })).toHaveAttribute(
      'href',
      '/store/1/orders/create'
    );
  });
});

describe('オーダー一覧のページ送り', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // 一覧ページは予約受付 inbox を同居させるため、その読み口も満たしておく
    mockedOrderApi.listReservationRequests.mockResolvedValue({
      rows: [],
      nextCursor: null,
    });
  });

  // 1 ページ 20 件で、101 件目以降にもページ送りで到達できることを固定する
  it('2 ページ目のボタンで 0 起点の page=1 を取得すること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      rows: [{ id: '1', business_date: '2026-07-03', course_minutes: 60, status: 'CREATED' }],
      page: 0,
      pageCount: 6,
      total: 120,
    });

    render(<OrderListPage />);
    await screen.findByText('2026-07-03');
    expect(mockedOrderApi.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,id,desc',
    });

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedOrderApi.list).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        sort: 'createdAt,id,desc',
      })
    );
  });
});
