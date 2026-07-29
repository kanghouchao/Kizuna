import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { PageResult } from '@/shared/api';
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
  created_at: '2026-01-01T00:00:00Z',
  ...override,
});

const paginated = (
  rows: Store[],
  override: Partial<PageResult<Store>> = {}
): PageResult<Store> => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
  ...override,
});

describe('店舗管理 3 画面の挙動', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getList.mockResolvedValue(paginated([]));
  });

  it('一覧は店舗名を表示すること', async () => {
    mockedApi.getList.mockResolvedValue(
      paginated([store({ id: '1', name: 'アルファ店' }), store({ id: '2', name: 'ベータ店' })])
    );

    render(<StoresPage />);

    expect(await screen.findByText('アルファ店')).toBeInTheDocument();
    expect(screen.getByText('ベータ店')).toBeInTheDocument();
  });

  // ドメインは店舗サイトへの導線。別ホストのため管理コンソールを閉じずに別タブで開く。
  it('一覧はドメインを別タブで開く店舗サイトのリンクとして表示すること', async () => {
    mockedApi.getList.mockResolvedValue(
      paginated([store({ id: '1', domain: 'alpha.example.com' })])
    );

    render(<StoresPage />);

    const link = await screen.findByRole('link', { name: /alpha\.example\.com/ });
    // プロトコル相対 URL。開発は http、本番は https と、現在のスキームを引き継ぐ
    expect(link).toHaveAttribute('href', '//alpha.example.com');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('検索は 0 起点の page/size/search のペイロードで再取得すること', async () => {
    render(<StoresPage />);
    await waitFor(() => expect(mockedApi.getList).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText('店舗を検索'), { target: { value: 'アルファ' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: 'アルファ',
      })
    );
  });

  it('ページ番号のクリックで該当ページを取得し、両端で前後ボタンが無効になること', async () => {
    // 応答の page は要求されたページを返す（外殻は応答の page を現在位置として描く）
    mockedApi.getList.mockImplementation(({ page }) =>
      Promise.resolve(
        paginated([store({ id: '1', name: 'アルファ店' })], { page, pageCount: 3, total: 25 })
      )
    );

    render(<StoresPage />);
    await screen.findByText('アルファ店');

    // 前後ボタンはモバイル用と nav 内アイコン用の 2 つがあるため nav 内に限定する
    const nav = () => screen.getByRole('navigation', { name: 'ページネーション' });
    // 1 ページ目では「前へ」が押せない
    expect(within(nav()).getByRole('button', { name: '前へ' })).toBeDisabled();
    expect(within(nav()).getByRole('button', { name: '次へ' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 1,
        size: 10,
        search: undefined,
      })
    );

    fireEvent.click(screen.getByRole('button', { name: '3' }));

    // 最終ページでは「次へ」が押せない
    await waitFor(() => expect(within(nav()).getByRole('button', { name: '次へ' })).toBeDisabled());
    expect(within(nav()).getByRole('button', { name: '前へ' })).toBeEnabled();
  });

  it('削除は確認ダイアログで承諾されたときだけ実行されること', async () => {
    mockedApi.getList.mockResolvedValue(paginated([store({ id: '7', name: 'アルファ店' })]));
    mockedApi.delete.mockResolvedValue(undefined);

    render(<StoresPage />);
    fireEvent.click(await screen.findByRole('button', { name: '削除' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('店舗「アルファ店」を削除しますか？');
    expect(dialog).toHaveTextContent('この操作は取り消せません。');

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(mockedApi.delete).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '削除' }));
    fireEvent.click(await screen.findByRole('button', { name: '削除する' }));

    await waitFor(() => expect(mockedApi.delete).toHaveBeenCalledWith('7'));
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
        domain: 'delta.example.com',
      })
    );
    mockedApi.update.mockResolvedValue(store({}));

    render(<StoreEditPage />);

    const nameInput = (await screen.findByLabelText(/店舗名/)) as HTMLInputElement;
    await waitFor(() => expect(nameInput.value).toBe('デルタ店'));
    expect(screen.getByText('delta.example.com')).toBeInTheDocument();

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

describe('店舗一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedApi.getList.mockResolvedValue(paginated([store({ id: '1', name: 'アルファ店' })]));
  });

  it('見出し・副題を備え、主アクションが button ロールのまま作成画面へ遷移すること', async () => {
    render(<StoresPage />);
    await screen.findByText('アルファ店');

    expect(screen.getByRole('heading', { level: 1, name: '店舗一覧' })).toBeInTheDocument();
    expect(screen.getByText('システム内の全ての店舗を管理します')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '店舗を追加' }));
    expect(mockPush).toHaveBeenCalledWith('/platform/stores/create');
  });

  // 検索語の変更だけでは取得は走らないため、送信そのものを捉えるには「現在ページが先頭へ戻る」
  // という送信ハンドラ固有の副作用を見る必要がある（form が無くなるとここだけが赤くなる）。
  it('検索の送信は現在ページを先頭へ戻すこと', async () => {
    mockedApi.getList.mockImplementation(({ page }) =>
      Promise.resolve(
        paginated([store({ id: '1', name: 'アルファ店' })], { page, pageCount: 3, total: 25 })
      )
    );

    render(<StoresPage />);
    await screen.findByText('アルファ店');

    fireEvent.click(screen.getByRole('button', { name: '2' }));
    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 1,
        size: 10,
        search: undefined,
      })
    );

    fireEvent.change(screen.getByLabelText('店舗を検索'), { target: { value: 'アルファ' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: 'アルファ',
      })
    );
  });

  // クリアは入力を空にすると同時に取り直す。検索語 state をそのまま読むと更新前の値で
  // 取得してしまうため、適用済み検索語は ref で持っている（その回帰を固定する）。
  it('クリアは検索語を空にして取り直すこと', async () => {
    render(<StoresPage />);
    await screen.findByText('アルファ店');

    fireEvent.change(screen.getByLabelText('店舗を検索'), { target: { value: 'アルファ' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));
    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: 'アルファ',
      })
    );

    fireEvent.click(screen.getByRole('button', { name: 'クリア' }));

    await waitFor(() =>
      expect(mockedApi.getList).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
      })
    );
    expect(screen.getByLabelText('店舗を検索')).toHaveValue('');
  });
});
