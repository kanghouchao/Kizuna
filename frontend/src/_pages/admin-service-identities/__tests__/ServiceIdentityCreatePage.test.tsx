import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { serviceIdentityApi, useMe, useStoreContext, type PermissionCode } from '@/entities/user';
import { ServiceIdentityCreatePage } from '..';

const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/entities/user', () => ({
  ...jest.requireActual('@/entities/user'),
  serviceIdentityApi: { create: jest.fn(), grantableRoles: jest.fn() },
  useStoreContext: jest.fn(),
}));

jest.mock('@/entities/user/model/MeContext', () => ({
  ...jest.requireActual('@/entities/user/model/MeContext'),
  useMe: jest.fn(),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedApi = serviceIdentityApi as jest.Mocked<typeof serviceIdentityApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;
const mockedStoreContext = useStoreContext as jest.MockedFunction<typeof useStoreContext>;
const mockedUseMe = useMe as jest.MockedFunction<typeof useMe>;
const grant = (...permissions: PermissionCode[]) =>
  mockedUseMe.mockReturnValue({
    me: {
      email: 'admin@example.com',
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
const HINT = /先にロール管理で自作ロールを作成してください/;

describe('サービスID追加ページ', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedStoreContext.mockReturnValue({
      stores: [
        { id: 1, name: '新宿店' },
        { id: 2, name: '渋谷店' },
      ],
      currentStoreId: undefined,
      loadFailed: false,
      reload: jest.fn(),
      switchStore: jest.fn(),
    } as unknown as ReturnType<typeof useStoreContext>);
    mockedApi.grantableRoles.mockResolvedValue([
      { id: 20, name: '失効バッチ', system: false, permission_count: 3 },
      { id: 21, name: '集計連携', system: false, permission_count: 1 },
    ]);
    mockedApi.create.mockResolvedValue({} as never);
    grant();
  });

  it('用途名と選択を作成 API へ送り、成功したら一覧へ戻る', async () => {
    render(<ServiceIdentityCreatePage />);
    expect(screen.getByRole('heading', { level: 1, name: 'サービスIDを追加' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('用途名'), {
      target: { value: '夜間ポイント失効' },
    });
    fireEvent.click(await screen.findByRole('checkbox', { name: '失効バッチ' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '渋谷店' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/service-identities'));
    expect(mockedApi.create).toHaveBeenCalledWith({
      display_name: '夜間ポイント失効',
      role_ids: [20],
      store_ids: [2],
    });
    expect(mockedNotify.success).toHaveBeenCalledWith('サービスIDを追加しました');
  });

  // 付与可能ロールの応答は権限の件数しか持たない。重なりを除けないので合計は出さない
  it('この設定の結果は用途名に敬称を付けず、権限をロールごとの件数で示す', async () => {
    render(<ServiceIdentityCreatePage />);
    const result = screen.getByRole('region', { name: 'この設定の結果' });
    expect(result).toHaveTextContent(
      '新しいサービスIDは 未選択 として、どの店舗のデータにもアクセスできません'
    );

    fireEvent.change(screen.getByLabelText('用途名'), { target: { value: '夜間失効' } });
    fireEvent.click(await screen.findByRole('checkbox', { name: '失効バッチ' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '集計連携' }));
    fireEvent.click(screen.getByRole('button', { name: 'すべて選択' }));

    expect(result).toHaveTextContent(
      '夜間失効は 失効バッチ・集計連携 として 新宿店・渋谷店 のデータ'
    );
    expect(within(result).getByRole('heading', { name: '付与される権限' })).toBeInTheDocument();
    expect(within(result).getByText('失効バッチ（3 件）')).toBeInTheDocument();
    expect(within(result).getByText('集計連携（1 件）')).toBeInTheDocument();
  });

  it('用途名・ロールが欠けていれば欄の傍に文言を出し、作成 API を呼ばない', async () => {
    render(<ServiceIdentityCreatePage />);
    await screen.findByRole('checkbox', { name: '失効バッチ' });
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    expect(await screen.findByText('用途名を入力してください')).toBeInTheDocument();
    expect(screen.getByText('ロールを 1 つ以上選択してください')).toBeInTheDocument();
    expect(mockedApi.create).not.toHaveBeenCalled();
  });

  it('店舗を選ばなければ担当店舗なしとして作成する', async () => {
    render(<ServiceIdentityCreatePage />);
    fireEvent.change(screen.getByLabelText('用途名'), { target: { value: '夜間失効' } });
    fireEvent.click(await screen.findByRole('checkbox', { name: '失効バッチ' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/admin/service-identities'));
    expect(mockedApi.create).toHaveBeenCalledWith({
      display_name: '夜間失効',
      role_ids: [20],
      store_ids: [],
    });
  });

  it('作成に失敗したらサーバの文言を出し、入力を残して画面に留まる', async () => {
    mockedApi.create.mockRejectedValue({
      response: { status: 400, data: { error: '指定された店舗が存在しません' } },
    });
    render(<ServiceIdentityCreatePage />);
    fireEvent.change(screen.getByLabelText('用途名'), { target: { value: '夜間失効' } });
    fireEvent.click(await screen.findByRole('checkbox', { name: '失効バッチ' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '新宿店' }));
    fireEvent.click(screen.getByRole('button', { name: '追加する' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('指定された店舗が存在しません')
    );
    expect(mockPush).not.toHaveBeenCalled();
    expect(screen.getByLabelText('用途名')).toHaveValue('夜間失効');
  });

  it('付与できるロールが無ければロール管理での作成を案内し、ROLE_MANAGE が無ければリンクを出さない', async () => {
    mockedApi.grantableRoles.mockResolvedValue([]);
    render(<ServiceIdentityCreatePage />);
    expect(await screen.findByText(HINT)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'ロール管理を開く' })).not.toBeInTheDocument();
  });

  it('ROLE_MANAGE を持つ利用者にはロール管理へのリンクを出す', async () => {
    mockedApi.grantableRoles.mockResolvedValue([]);
    grant('ROLE_MANAGE');
    render(<ServiceIdentityCreatePage />);
    expect(await screen.findByRole('link', { name: 'ロール管理を開く' })).toHaveAttribute(
      'href',
      '/admin/roles'
    );
  });

  it('ロールの取得に失敗したら失敗を表示し、作成の案内は出さない', async () => {
    mockedApi.grantableRoles.mockRejectedValue(new Error('boom'));
    render(<ServiceIdentityCreatePage />);
    expect(await screen.findByText('ロール一覧の取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText(HINT)).not.toBeInTheDocument();
  });

  it('キャンセルは一覧への link である', () => {
    render(<ServiceIdentityCreatePage />);
    expect(screen.getByRole('link', { name: 'キャンセル' })).toHaveAttribute(
      'href',
      '/admin/service-identities'
    );
  });
});
