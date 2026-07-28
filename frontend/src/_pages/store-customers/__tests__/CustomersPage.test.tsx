import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CustomersPage from '../ui/CustomersPage';
import CustomerCreatePage from '../ui/CustomerCreatePage';
import { CustomerForm } from '../ui/CustomerForm';
import { customerApi } from '@/entities/customer';

jest.mock('@/entities/customer', () => ({
  customerApi: {
    list: jest.fn(),
    create: jest.fn(),
  },
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  useParams: () => ({ storeId: '1' }),
}));

const mockedCustomerApi = customerApi as jest.Mocked<typeof customerApi>;

describe('店側顧客画面と API JSON（snake_case）の整合', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('一覧はバックエンドが実際に返す snake_case のフィールドを表示すること', async () => {
    // バックエンドは Jackson グローバル SNAKE_CASE（既知の実レスポンス形）
    mockedCustomerApi.list.mockResolvedValue({
      rows: [
        {
          id: '1',
          name: '山田太郎',
          phone_number: '090-1111-2222',
          line_id: 'yamada',
          rank: 'GOLD',
          classification: '常連',
          points: 120,
          ng_type: '注意',
        },
      ],
      page: 0,
      pageCount: 1,
      total: 1,
    } as never);

    render(<CustomersPage />);

    expect(await screen.findByText('山田太郎')).toBeInTheDocument();
    expect(screen.getByText('090-1111-2222')).toBeInTheDocument();
    expect(screen.getByText('yamada')).toBeInTheDocument();
    // NG バッジは ng_type をそのまま表示する
    expect(screen.getByText('注意')).toBeInTheDocument();
  });

  it('新規登録はバックエンドの DTO に合わせ snake_case キーで POST すること', async () => {
    mockedCustomerApi.create.mockResolvedValue({} as never);

    render(<CustomerCreatePage />);
    // name は required のため、未入力だと create が呼ばれない
    fireEvent.change(screen.getByLabelText('名前 *'), { target: { value: '田中花子' } });
    fireEvent.click(screen.getByRole('button', { name: '保存する' }));

    await waitFor(() => expect(mockedCustomerApi.create).toHaveBeenCalledTimes(1));
    const body = mockedCustomerApi.create.mock.calls[0][0] as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('name', '田中花子');
    // 未操作チェックボックスは boolean false のまま（挙動維持の錨）
    expect(body).toHaveProperty('has_pet', false);
    // defaultValues の rank はそのまま送信される
    expect(body).toHaveProperty('rank', 'SILVER');
    // 空文字フィールドは toCustomerRequest で undefined に落ちる
    expect(body).toHaveProperty('phone_number', undefined);
    // camelCase キーが混入しないこと
    expect(body).not.toHaveProperty('phoneNumber');
    expect(body).not.toHaveProperty('hasPet');
    expect(body).not.toHaveProperty('lineId');
  });

  it('編集時の has_pet:true が controlled チェックボックスに反映されること', () => {
    render(<CustomerForm initialData={{ name: '太郎', has_pet: true }} onSubmit={jest.fn()} />);

    expect(screen.getByRole('checkbox')).toBeChecked();
    // ラベル文字クリックでトグルできること（label→control の関連付け維持）
    fireEvent.click(screen.getByText('ペットあり'));
    expect(screen.getByRole('checkbox')).not.toBeChecked();
  });
});

describe('顧客一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCustomerApi.list.mockResolvedValue({
      rows: [],
      page: 0,
      pageCount: 0,
      total: 0,
    } as never);
  });

  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: '顧客管理' })).toBeInTheDocument();
    expect(screen.getByText('顧客情報の登録・編集ができます。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規顧客登録' })).toHaveAttribute(
      'href',
      '/store/1/customers/create'
    );
  });

  it('検索の送信で入力中の絞り込み条件を 1 ページ目から取り直すこと', async () => {
    render(<CustomersPage />);
    await screen.findByText('顧客が登録されていません');

    fireEvent.change(screen.getByPlaceholderText('名前・電話番号・LINE ID で検索...'), {
      target: { value: '山田' },
    });
    fireEvent.change(screen.getByPlaceholderText('ランク'), { target: { value: 'GOLD' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedCustomerApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        sort: 'createdAt,desc',
        search: '山田',
        rank: 'GOLD',
        classification: undefined,
      })
    );
  });
});
