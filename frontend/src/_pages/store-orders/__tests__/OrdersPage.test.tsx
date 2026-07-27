import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import OrderListPage from '../ui/OrdersPage';
import CreateOrderPage from '../ui/OrderCreatePage';
import { orderApi } from '@/entities/order';

jest.mock('@/entities/order', () => ({
  orderApi: {
    list: jest.fn(),
    create: jest.fn(),
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

describe('店側オーダー画面と API JSON（snake_case）の整合', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('一覧はバックエンドが実際に返す snake_case のフィールドを表示すること', async () => {
    // バックエンドは Jackson グローバル SNAKE_CASE（既知の実レスポンス形）
    mockedOrderApi.list.mockResolvedValue({
      content: [
        {
          id: '1',
          business_date: '2026-07-03',
          customer_name: '山田太郎',
          cast_name: '花子',
          course_minutes: 60,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          used_points: 0,
          manual_grant_points: 0,
          status: 'CREATED',
        },
      ],
      total_pages: 1,
      total_elements: 1,
      size: 100,
      number: 0,
    } as never);

    render(<OrderListPage />);

    expect(await screen.findByText('2026-07-03')).toBeInTheDocument();
    expect(screen.getByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('花子')).toBeInTheDocument();
    expect(screen.getByText('60 分')).toBeInTheDocument();
  });

  it('一覧の遷移リンクは店舗スコープのパスを指すこと', async () => {
    mockedOrderApi.list.mockResolvedValue({
      content: [
        {
          id: '7',
          business_date: '2026-07-03',
          customer_name: '山田太郎',
          cast_name: '花子',
          course_minutes: 60,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          used_points: 0,
          manual_grant_points: 0,
          status: 'CREATED',
        },
      ],
      total_pages: 1,
      total_elements: 1,
      size: 100,
      number: 0,
    } as never);

    render(<OrderListPage />);
    await screen.findByText('2026-07-03');

    expect(screen.getByRole('link', { name: /新規オーダー登録/ })).toHaveAttribute(
      'href',
      '/store/1/orders/create'
    );
    const links = screen.getAllByRole('link');
    expect(links.map(link => link.getAttribute('href'))).toContain('/store/1/orders/7/edit');
  });

  it('キャスト未指名はフリー表記になること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      content: [
        {
          id: '8',
          business_date: '2026-07-04',
          customer_name: '鈴木花子',
          cast_name: null,
          course_minutes: 90,
          extension_minutes: 0,
          option_codes: [],
          manual_discount: 0,
          used_points: 0,
          manual_grant_points: 0,
          status: 'CREATED',
        },
      ],
      total_pages: 1,
      total_elements: 1,
      size: 100,
      number: 0,
    } as never);

    render(<OrderListPage />);

    expect(await screen.findByText('フリー')).toBeInTheDocument();
  });

  it('オーダーが0件なら不在メッセージを表示すること', async () => {
    mockedOrderApi.list.mockResolvedValue({
      content: [],
      total_pages: 0,
      total_elements: 0,
      size: 100,
      number: 0,
    } as never);

    render(<OrderListPage />);

    expect(await screen.findByText('オーダーがありません')).toBeInTheDocument();
  });

  it('新規登録はバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedOrderApi.create.mockResolvedValue({} as never);

    render(<CreateOrderPage />);
    fireEvent.click(screen.getByRole('button', { name: '登録する' }));

    await waitFor(() => expect(mockedOrderApi.create).toHaveBeenCalledTimes(1));
    const body = mockedOrderApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('business_date');
    expect(body).toHaveProperty('course_minutes', 60);
    // セレクト未操作時の既定ペイロード（挙動維持の錨）
    expect(body).toHaveProperty('classification', 'ーー');
    expect(body).toHaveProperty('has_pet', false);
    expect(body).toHaveProperty('discount_name', '');
    // 受付未選択は '' のまま → マッピングで undefined になり送信時に欠落する
    expect(body).toHaveProperty('receptionist_id', undefined);
    expect(body).not.toHaveProperty('store_name');
    expect(body).not.toHaveProperty('storeName');
    expect(body).not.toHaveProperty('businessDate');
  });
});

describe('オーダー一覧ページの外殻', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedOrderApi.list.mockResolvedValue({
      content: [],
      total_pages: 0,
      total_elements: 0,
      size: 100,
      number: 0,
    } as never);
  });

  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    const { container } = render(<OrderListPage />);
    await screen.findByText('オーダーがありません');

    // e2e（hybrid-console-access）は見出し名 'オーダー一覧' の完全一致で到達確認する
    expect(screen.getByRole('heading', { level: 1, name: 'オーダー一覧' })).toBeInTheDocument();
    // 外殻の class 文字列は DESIGN.md が規格として定めた面そのもの。
    expect(container.firstElementChild).toHaveClass('space-y-6');
    expect(screen.getByText('当日の注文状況を確認・管理できます。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規オーダー登録' })).toHaveAttribute(
      'href',
      '/store/1/orders/create'
    );
  });
});
