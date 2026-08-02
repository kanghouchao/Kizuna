import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CastListPage from '../ui/CastsPage';
import { CastResponse, castApi } from '@/entities/cast';
import { TokenClaims, readTokenClaims } from '@/shared/lib';

jest.mock('next/navigation', () => ({
  useParams: () => ({ storeId: '1' }),
}));

// hasPermission は実物のまま（PERM_ 接頭辞の対応も検証対象に含める）
jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  readTokenClaims: jest.fn(),
}));

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castApi: {
      ...actual.castApi,
      list: jest.fn(),
      issueInvitation: jest.fn(),
      delete: jest.fn(),
    },
  };
});

const mockedCastApi = castApi as jest.Mocked<typeof castApi>;
const mockedReadClaims = readTokenClaims as jest.MockedFunction<typeof readTokenClaims>;

/** 指定権限を claim（PERM_ 接頭辞）として持つ token claim を返すヘルパ（UI 出し分けは権限ベース）。 */
function claimsWith(permissions: string[]): TokenClaims {
  return {
    authorities: permissions.map(permission => `PERM_${permission}`),
    userType: 'STAFF',
    storeBridge: true,
  };
}

const cast: CastResponse = {
  id: 'cast-1',
  name: '花子',
  status: 'ACTIVE',
  invitation_status: 'NOT_INVITED',
  created_at: '2026-07-01T00:00:00Z',
  updated_at: '2026-07-01T00:00:00Z',
};

const toPage = (rows: CastResponse[]) => ({
  rows,
  page: 0,
  pageCount: 1,
  total: rows.length,
});

describe('招待発行モーダルが一覧の再取得中もアンマウントされない', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE', 'CAST_INVITE']));
  });

  it('発行成功直後、一覧の再取得が isLoading=true を経ても発行成功モーダルが表示され続けること', async () => {
    // 1回目（初回取得）は即解決、2回目（発行後の refetch）は手動で解決タイミングを制御し、
    // isLoading=true が実際にコミットされる瞬間を作る（実機の遅延ネットワークを模す）。
    let resolveRefetch!: (value: ReturnType<typeof toPage>) => void;
    mockedCastApi.list.mockResolvedValueOnce(toPage([cast])).mockImplementationOnce(
      () =>
        new Promise(resolve => {
          resolveRefetch = resolve;
        })
    );
    mockedCastApi.issueInvitation.mockResolvedValue({
      token: 'tok-123',
      expires_at: '2026-07-18T00:00:00Z',
    });

    render(<CastListPage />);
    await screen.findByText('花子');

    fireEvent.click(screen.getByRole('button', { name: '招待を発行' }));

    // 再取得中（一覧が「読み込み中...」に切り替わる）でもモーダルは表示され続けること
    await screen.findByText('読み込み中...');
    expect(screen.getByText('招待リンクを発行しました')).toBeInTheDocument();
    const linkInput = screen.getByLabelText('招待リンク') as HTMLInputElement;
    expect(linkInput.value).toContain('/platform/invite/tok-123');

    resolveRefetch(toPage([{ ...cast, invitation_status: 'INVITED' }]));
    await waitFor(() => expect(mockedCastApi.list).toHaveBeenCalledTimes(2));
    // 再取得完了後もモーダルは閉じられるまで表示されたままであること
    expect(screen.getByText('招待リンクを発行しました')).toBeInTheDocument();
  });
});

describe('招待発行ボタンの表示は CAST_INVITE 能力限定', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.list.mockResolvedValue(toPage([cast]));
  });

  it('CAST_INVITE 能力があれば行内に「招待を発行」ボタンが表示されること', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE', 'CAST_INVITE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(await screen.findByRole('button', { name: '招待を発行' })).toBeInTheDocument();
  });

  it('CAST_INVITE 能力が無ければ「招待を発行」ボタンが表示されず、招待状態バッジは表示されること', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(screen.queryByRole('button', { name: '招待を発行' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再発行' })).not.toBeInTheDocument();
    expect(screen.getByText('未招待')).toBeInTheDocument();
  });
});

describe('カスタムフィールド管理への入口リンクは CAST_FIELD_DEF_MANAGE 能力限定', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.list.mockResolvedValue(toPage([cast]));
  });

  it('CAST_FIELD_DEF_MANAGE 能力があれば定義管理ページ(/store/casts/fields)への入口リンクが表示されること', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE', 'CAST_FIELD_DEF_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    const link = await screen.findByRole('link', { name: 'カスタムフィールド管理' });
    expect(link).toHaveAttribute('href', '/store/1/casts/fields');
  });

  it('CAST_FIELD_DEF_MANAGE 能力が無ければ定義管理ページへの入口リンクが表示されないこと', async () => {
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(screen.queryByRole('link', { name: 'カスタムフィールド管理' })).not.toBeInTheDocument();
  });
});

describe('キャスト一覧ページ固有の要素', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.list.mockResolvedValue(toPage([]));
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE']));
  });

  it('見出し（h1）・副題・主アクションのリンク先を備えること', async () => {
    render(<CastListPage />);
    await screen.findByText('キャストが登録されていません');

    expect(screen.getByRole('heading', { level: 1, name: 'キャスト管理' })).toBeInTheDocument();
    expect(screen.getByText('キャスト情報の登録・編集ができます。')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '新規キャスト登録' })).toHaveAttribute(
      'href',
      '/store/1/casts/create'
    );
  });
});

describe('キャスト一覧のページ送りと検索', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedReadClaims.mockReturnValue(claimsWith(['CAST_MANAGE']));
  });

  // 1 ページ 20 件で、101 人目以降にもページ送りで到達できることを固定する
  it('2 ページ目のボタンで 0 起点の page=1 を取得すること', async () => {
    mockedCastApi.list.mockResolvedValue({ rows: [cast], page: 0, pageCount: 6, total: 120 });

    render(<CastListPage />);
    await screen.findByText('花子');
    expect(mockedCastApi.list).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'displayOrder,id,asc',
      search: undefined,
    });

    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedCastApi.list).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        sort: 'displayOrder,id,asc',
        search: undefined,
      })
    );
  });

  it('送信していない検索語はページ送りに紛れ込まないこと', async () => {
    mockedCastApi.list.mockResolvedValue({ rows: [cast], page: 0, pageCount: 6, total: 120 });

    render(<CastListPage />);
    await screen.findByText('花子');

    // 入力しただけ（検索は未送信）でページ送りすると、表示中の結果集合とページが食い違う
    fireEvent.change(screen.getByPlaceholderText('名前で検索...'), { target: { value: '花' } });
    fireEvent.click(screen.getByRole('button', { name: '2' }));

    await waitFor(() =>
      expect(mockedCastApi.list).toHaveBeenLastCalledWith({
        page: 1,
        size: 20,
        sort: 'displayOrder,id,asc',
        search: undefined,
      })
    );
  });

  it('検索の送信で入力中の名前を 1 ページ目から取り直すこと', async () => {
    mockedCastApi.list.mockResolvedValue(toPage([cast]));

    render(<CastListPage />);
    await screen.findByText('花子');

    fireEvent.change(screen.getByPlaceholderText('名前で検索...'), { target: { value: '花' } });
    fireEvent.click(screen.getByRole('button', { name: '検索' }));

    await waitFor(() =>
      expect(mockedCastApi.list).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        sort: 'displayOrder,id,asc',
        search: '花',
      })
    );
  });
});
