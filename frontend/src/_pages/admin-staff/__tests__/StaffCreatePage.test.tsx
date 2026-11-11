import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { staffApi, useStoreContext } from '@/entities/user';
import { StaffCreatePage } from '..';

const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  staffApi: { create: jest.fn(), grantableRoles: jest.fn() },
  useStoreContext: jest.fn(),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedStaffApi = staffApi as jest.Mocked<typeof staffApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;

const givenStores = (
  stores: { id: number; name: string }[] | null,
  override: { loadFailed?: boolean; reload?: () => void } = {}
) => {
  const reload = override.reload ?? jest.fn();
  mockedStoreContext.mockReturnValue({
    stores,
    currentStoreId: undefined,
    loadFailed: override.loadFailed ?? false,
    reload,
  } as unknown as ReturnType<typeof useStoreContext>);
  return reload;
};

const fillIdentity = () => {
  fireEvent.change(screen.getByLabelText('メールアドレス'), {
    target: { value: 'new@example.com' },
  });
  fireEvent.change(screen.getByLabelText('初期パスワード'), { target: { value: 'pass1234' } });
  fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '新人 花子' } });
};

describe('従業員追加ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    givenStores([
      { id: 1, name: '新宿店' },
      { id: 2, name: '渋谷店' },
    ]);
    mockedStaffApi.grantableRoles.mockResolvedValue([
      {
        id: 10,
        name: '店長',
        system: true,
        permissions: [
          { code: 'ORDER_MANAGE', label: '受注の管理' },
          { code: 'CUSTOMER_MANAGE', label: '顧客の管理' },
        ],
      },
      {
        id: 11,
        name: 'スタッフ',
        system: true,
        permissions: [{ code: 'ORDER_MANAGE', label: '受注の管理' }],
      },
    ]);
    mockedStaffApi.create.mockResolvedValue({} as never);
  });

  it('入力と選択を作成 API へ送り、成功したら一覧へ戻る', async () => {
    render(<StaffCreatePage />);
    expect(screen.getByRole('heading', { level: 1, name: '従業員を追加' })).toBeInTheDocument();

    fillIdentity();
    fireEvent.click(await screen.findByRole('checkbox', { name: /スタッフ/ }));
    fireEvent.click(screen.getByRole('checkbox', { name: '渋谷店' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/staff'));
    expect(mockedStaffApi.create).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'pass1234',
      display_name: '新人 花子',
      role_ids: [11],
      store_ids: [2],
    });
    expect(mockedNotify.success).toHaveBeenCalledWith('従業員を追加しました');
  });

  it('ロール・本人の欄が欠けていれば欄の傍に文言を出し、作成 API を呼ばない', async () => {
    render(<StaffCreatePage />);
    await screen.findByRole('checkbox', { name: '店長' });
    fireEvent.change(screen.getByLabelText('メールアドレス'), { target: { value: 'invalid' } });

    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    expect(await screen.findByText('ロールを 1 つ以上選択してください')).toBeInTheDocument();
    expect(screen.getByText('氏名を入力してください')).toBeInTheDocument();
    expect(screen.getByText('初期パスワードを入力してください')).toBeInTheDocument();
    expect(screen.getByLabelText('メールアドレス')).toHaveAttribute('aria-invalid', 'true');
    expect(mockedStaffApi.create).not.toHaveBeenCalled();
  });

  it('作成に失敗したらサーバの文言を出し、入力を残して画面に留まる', async () => {
    mockedStaffApi.create.mockRejectedValue({
      response: { status: 403, data: { error: '担当外の店舗は指定できません' } },
    });
    render(<StaffCreatePage />);
    fillIdentity();
    fireEvent.click(await screen.findByRole('checkbox', { name: '店長' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '新宿店' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('担当外の店舗は指定できません')
    );
    expect(mockPush).not.toHaveBeenCalled();
    expect(screen.getByLabelText('氏名')).toHaveValue('新人 花子');
    expect(screen.getByRole('button', { name: '追加する' })).toBeEnabled();
  });

  it('この設定の結果が氏名・ロール・店舗の選択に追従する', async () => {
    render(<StaffCreatePage />);
    const result = screen.getByRole('region', { name: 'この設定の結果' });
    expect(result).toHaveTextContent(
      '新しい従業員は 未選択 として、どの店舗のデータにもアクセスできません'
    );
    expect(within(result).getByRole('heading', { name: '対象店舗（0 店舗）' })).toBeInTheDocument();
    expect(within(result).getByText('担当店舗なし')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('氏名'), { target: { value: '新人 花子' } });
    fireEvent.click(await screen.findByRole('checkbox', { name: '店長' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '新宿店' }));

    expect(result).toHaveTextContent(
      '新人 花子さんは 店長 として 新宿店 のデータにアクセスできます'
    );
    expect(
      within(result).getByRole('heading', { name: '付与される権限（2 件）' })
    ).toBeInTheDocument();
    expect(within(result).getByText('受注の管理')).toBeInTheDocument();
    expect(within(result).getByText('顧客の管理')).toBeInTheDocument();

    // 2 ロールが共有する権限は 1 件として数える
    fireEvent.click(screen.getByRole('checkbox', { name: 'スタッフ' }));
    expect(
      within(result).getByRole('heading', { name: '付与される権限（2 件）' })
    ).toBeInTheDocument();
    expect(within(result).getAllByText('受注の管理')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
    expect(result).toHaveTextContent('として 新宿店・渋谷店 のデータ');
    expect(within(result).getByRole('heading', { name: '対象店舗（2 店舗）' })).toBeInTheDocument();
  });

  it('全店舗の選択肢は無く、すべて選択はその時点の店舗を個別に送る', async () => {
    render(<StaffCreatePage />);
    expect(screen.queryByRole('radio', { name: '全店舗' })).not.toBeInTheDocument();
    fillIdentity();
    fireEvent.click(await screen.findByRole('checkbox', { name: '店長' }));
    fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/staff'));
    expect(mockedStaffApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ store_ids: [1, 2] })
    );
  });

  // 空の店舗集合は「どの店舗も担当しない」で、全店舗の意味は持たない
  it('店舗を選ばなければ担当店舗なしとして作成する', async () => {
    render(<StaffCreatePage />);
    fillIdentity();
    fireEvent.click(await screen.findByRole('checkbox', { name: '店長' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/staff'));
    expect(mockedStaffApi.create).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'pass1234',
      display_name: '新人 花子',
      role_ids: [10],
      store_ids: [],
    });
  });

  it('多店舗でもすべて選択・すべて解除で個別店舗をまとめて切り替えられる', async () => {
    render(<StaffCreatePage />);
    fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));
    expect(screen.getByText('2 / 2 店舗を選択中')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '渋谷店' })).toBeChecked();

    fireEvent.click(screen.getByRole('button', { name: 'すべて解除' }));
    expect(screen.getByText('0 / 2 店舗を選択中')).toBeInTheDocument();
  });

  it('店舗目録の取得に失敗したら個別店舗の欄が失敗を名乗り、再試行で取り直す', async () => {
    const reload = givenStores(null, { loadFailed: true });
    render(<StaffCreatePage />);

    fireEvent.click(screen.getByRole('button', { name: '再試行' }));
    expect(screen.getByText('店舗一覧の取得に失敗しました')).toBeInTheDocument();
    expect(reload).toHaveBeenCalled();
  });

  // 管理者自身の保存済みパスワードが新しい従業員の初期パスワードへ自動入力されないようにする
  it('初期パスワード欄は新しいパスワードとして自動入力を扱う', () => {
    render(<StaffCreatePage />);
    expect(screen.getByLabelText('初期パスワード')).toHaveAttribute('autocomplete', 'new-password');
  });

  it('キャンセルは一覧への link である', () => {
    render(<StaffCreatePage />);
    expect(screen.getByRole('link', { name: 'キャンセル' })).toHaveAttribute(
      'href',
      '/admin/staff'
    );
  });
});
