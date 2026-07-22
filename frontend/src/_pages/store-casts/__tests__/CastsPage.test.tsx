import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CastListPage from '../ui/CastsPage';
import { CastResponse, castApi } from '@/entities/cast';
import { platformAuthApi, PlatformMeResponse } from '@/entities/user';

jest.mock('next/navigation', () => ({
  useParams: () => ({ storeId: '1' }),
}));

jest.mock('@/entities/user', () => {
  const actual = jest.requireActual('@/entities/user');
  return {
    ...actual,
    platformAuthApi: {
      ...actual.platformAuthApi,
      me: jest.fn(),
    },
  };
});

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
const mockedMe = platformAuthApi.me as jest.MockedFunction<typeof platformAuthApi.me>;

/** 指定能力を持つ /me 応答を返すヘルパ（UI 出し分けは能力ベース — #398）。 */
function meWith(capabilities: PlatformMeResponse['capabilities']): PlatformMeResponse {
  return {
    email: 'staff@kizuna.test',
    display_name: '店舗スタッフ',
    user_type: 'STAFF',
    capabilities,
    console: 'store',
    store_bridge: true,
    store_scope_type: 'SPECIFIC_STORES',
    store_ids: [1],
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

const toPage = (content: CastResponse[]) => ({
  content,
  total_pages: 1,
  total_elements: content.length,
  size: 100,
  number: 0,
});

describe('招待発行モーダルが一覧の再取得中もアンマウントされない（#327）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedMe.mockResolvedValue(meWith(['CAST_MANAGE', 'CAST_INVITE']));
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

describe('招待発行ボタンの表示は CAST_INVITE 能力限定（#327 / #398）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.list.mockResolvedValue(toPage([cast]));
  });

  it('CAST_INVITE 能力があれば行内に「招待を発行」ボタンが表示されること', async () => {
    mockedMe.mockResolvedValue(meWith(['CAST_MANAGE', 'CAST_INVITE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(await screen.findByRole('button', { name: '招待を発行' })).toBeInTheDocument();
  });

  it('CAST_INVITE 能力が無ければ「招待を発行」ボタンが表示されず、招待状態バッジは表示されること', async () => {
    mockedMe.mockResolvedValue(meWith(['CAST_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(screen.queryByRole('button', { name: '招待を発行' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '再発行' })).not.toBeInTheDocument();
    expect(screen.getByText('未招待')).toBeInTheDocument();
  });
});

describe('カスタムフィールド管理への入口リンクは CAST_FIELD_DEF_MANAGE 能力限定（#277 / #398）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.list.mockResolvedValue(toPage([cast]));
  });

  it('CAST_FIELD_DEF_MANAGE 能力があれば定義管理ページ(/store/casts/fields)への入口リンクが表示されること', async () => {
    mockedMe.mockResolvedValue(meWith(['CAST_MANAGE', 'CAST_FIELD_DEF_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    const link = await screen.findByRole('link', { name: 'カスタムフィールド管理' });
    expect(link).toHaveAttribute('href', '/store/1/casts/fields');
  });

  it('CAST_FIELD_DEF_MANAGE 能力が無ければ定義管理ページへの入口リンクが表示されないこと', async () => {
    mockedMe.mockResolvedValue(meWith(['CAST_MANAGE']));

    render(<CastListPage />);
    await screen.findByText('花子');

    expect(screen.queryByRole('link', { name: 'カスタムフィールド管理' })).not.toBeInTheDocument();
  });
});
