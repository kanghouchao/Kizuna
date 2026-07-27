import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PaginatedResponse } from '@/shared/api';
import { Store, platformStoreApi } from '@/entities/store';
import StoresPage from '../ui/StoresPage';
import StoreCreatePage from '../ui/StoreCreatePage';
import StoreEditPage from '../ui/StoreEditPage';

const mockPush = jest.fn();

jest.mock('@/entities/store', () => ({
  platformStoreApi: {
    getList: jest.fn(),
    getById: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: jest.fn() }),
}));

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useParams: () => ({ id: '1' }),
}));

jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
}));

const mockedApi = platformStoreApi as jest.Mocked<typeof platformStoreApi>;

const store = (override: Partial<Store>): Store => ({
  id: '1',
  name: 'アルファ店',
  email: 'alpha@example.com',
  domain: 'alpha.example.com',
  domains: ['alpha.example.com'],
  is_active: true,
  created_at: '2026-01-01T00:00:00Z',
  ...override,
});

const paginated = (
  data: Store[],
  override: Partial<PaginatedResponse<Store>> = {}
): PaginatedResponse<Store> => ({
  data,
  current_page: 1,
  from: 1,
  last_page: 1,
  per_page: 10,
  to: data.length,
  total: data.length,
  first_page_url: '',
  last_page_url: '',
  next_page_url: null,
  prev_page_url: null,
  ...override,
});

describe('店舗管理 3 画面の挙動', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getList.mockResolvedValue(paginated([]));
  });

  it('一覧は店舗名と有効/無効の状態を表示すること', async () => {
    mockedApi.getList.mockResolvedValue(
      paginated([
        store({ id: '1', name: 'アルファ店', is_active: true }),
        store({ id: '2', name: 'ベータ店', is_active: false }),
      ])
    );

    render(<StoresPage />);

    expect(await screen.findByText('アルファ店')).toBeInTheDocument();
    expect(screen.getByText('ベータ店')).toBeInTheDocument();
    expect(screen.getByText('有効')).toBeInTheDocument();
    expect(screen.getByText('無効')).toBeInTheDocument();
  });

  it('検索は page/per_page/search のペイロードで再取得すること', async () => {
    render(<StoresPage />);
    await waitFor(() => expect(mockedApi.getList).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('店舗を検索'), { target: { value: 'アルファ' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 1,
        per_page: 10,
        search: 'アルファ',
      })
    );
  });

  it('ページ番号のクリックで該当ページを取得し、両端で前後ボタンが無効になること', async () => {
    mockedApi.getList.mockResolvedValue(
      paginated([store({ id: '1', name: 'アルファ店' })], { last_page: 3, to: 10, total: 25 })
    );

    render(<StoresPage />);
    await screen.findByText('アルファ店');

    // 1 ページ目では「前へ」が押せない
    expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次へ' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 2,
        per_page: 10,
        search: undefined,
      })
    );

    fireEvent.click(screen.getByRole('button', { name: '3' }));

    // 最終ページでは「次へ」が押せない
    await waitFor(() => expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled());
    expect(screen.getByRole('button', { name: '前へ' })).toBeEnabled();
  });

  it('削除は confirm で承諾されたときだけ実行されること', async () => {
    mockedApi.getList.mockResolvedValue(paginated([store({ id: '7', name: 'アルファ店' })]));
    mockedApi.delete.mockResolvedValue(undefined);
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(false);

    render(<StoresPage />);
    fireEvent.click(await screen.findByRole('button', { name: '削除' }));

    expect(confirmSpy).toHaveBeenCalledWith(
      '店舗「アルファ店」を削除しますか？この操作は取り消せません。'
    );
    expect(mockedApi.delete).not.toHaveBeenCalled();

    confirmSpy.mockReturnValue(true);
    fireEvent.click(screen.getByRole('button', { name: '削除' }));

    await waitFor(() => expect(mockedApi.delete).toHaveBeenCalledWith('7'));
    confirmSpy.mockRestore();
  });

  it('新規作成は name/domain/email を送信し一覧へ遷移すること', async () => {
    mockedApi.create.mockResolvedValue(store({}));

    render(<StoreCreatePage />);
    fireEvent.change(screen.getByLabelText(/店舗名/), { target: { value: 'ガンマ店' } });
    fireEvent.change(screen.getByLabelText(/ドメイン/), { target: { value: 'Gamma.example.com' } });
    fireEvent.change(screen.getByLabelText(/連絡用メール/), {
      target: { value: 'gamma@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: '店舗を作成' }));

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledTimes(1));
    expect(mockedApi.create).toHaveBeenCalledWith({
      name: 'ガンマ店',
      // 入力時に小文字化される
      domain: 'gamma.example.com',
      email: 'gamma@example.com',
    });
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/platform/stores'));
  });

  it('新規作成はドメイン形式が不正なら送信せずエラーを表示すること', async () => {
    render(<StoreCreatePage />);
    fireEvent.change(screen.getByLabelText(/店舗名/), { target: { value: 'ガンマ店' } });
    fireEvent.change(screen.getByLabelText(/ドメイン/), { target: { value: '不正 ドメイン' } });
    fireEvent.change(screen.getByLabelText(/連絡用メール/), {
      target: { value: 'gamma@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: '店舗を作成' }));

    expect(await screen.findByText('ドメイン形式が正しくありません')).toBeInTheDocument();
    expect(mockedApi.create).not.toHaveBeenCalled();
  });

  it('編集は取得値を初期表示し name/email を送信して一覧へ遷移すること', async () => {
    mockedApi.getById.mockResolvedValue(
      store({
        id: '1',
        name: 'デルタ店',
        email: 'delta@example.com',
        domains: ['delta.example.com', 'delta2.example.com'],
      })
    );
    mockedApi.update.mockResolvedValue(store({}));

    render(<StoreEditPage />);

    const nameInput = (await screen.findByLabelText(/店舗名/)) as HTMLInputElement;
    await waitFor(() => expect(nameInput.value).toBe('デルタ店'));
    expect(screen.getByText('delta2.example.com')).toBeInTheDocument();

    fireEvent.change(nameInput, { target: { value: 'デルタ本店' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(mockedApi.update).toHaveBeenCalledWith('1', {
        name: 'デルタ本店',
        email: 'delta@example.com',
      })
    );
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/platform/stores'));
  });
});

describe('店舗一覧ページの外殻', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getList.mockResolvedValue(paginated([store({ id: '1', name: 'アルファ店' })]));
  });

  it('見出し・副題を備え、主アクションが button ロールのまま作成画面へ遷移すること', async () => {
    render(<StoresPage />);
    await screen.findByText('アルファ店');

    // 見出しレベルは他の一覧ページに揃えて h2→h1 へ変える予定のため、ここでは level を断言しない
    expect(screen.getByRole('heading', { name: '店舗一覧' })).toBeInTheDocument();
    expect(screen.getByText('システム内の全ての店舗を管理します')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '店舗を追加' }));
    expect(mockPush).toHaveBeenCalledWith('/platform/stores/create');
  });

  // 検索語の変更だけでも取得は走るため、送信そのものを捉えるには「現在ページが 1 に戻る」
  // という送信ハンドラ固有の副作用を見る必要がある（form が無くなるとここだけが赤くなる）。
  it('検索の送信は現在ページを 1 へ戻すこと', async () => {
    mockedApi.getList.mockResolvedValue(
      paginated([store({ id: '1', name: 'アルファ店' })], { last_page: 3, to: 10, total: 25 })
    );

    render(<StoresPage />);
    await screen.findByText('アルファ店');

    fireEvent.click(screen.getByRole('button', { name: '2' }));
    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 2,
        per_page: 10,
        search: undefined,
      })
    );

    fireEvent.change(screen.getByLabelText('店舗を検索'), { target: { value: 'アルファ' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 1,
        per_page: 10,
        search: 'アルファ',
      })
    );
  });
});
