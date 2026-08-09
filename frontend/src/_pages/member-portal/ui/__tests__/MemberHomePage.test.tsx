import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemberHomePage } from '../MemberHomePage';
import { memberApi } from '@/entities/member';

jest.mock('@/entities/member', () => ({
  memberApi: { home: jest.fn() },
}));

const mockLogout = jest.fn();
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: mockLogout }),
}));

const mockedHome = memberApi.home as jest.Mock;

describe('MemberHomePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('会員コードを QR とテキストで表示する', async () => {
    mockedHome.mockResolvedValue({ member_code: '123456789012', display_name: '会員花子' });

    render(<MemberHomePage />);

    expect(await screen.findByText('1234 5678 9012')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '会員コードQR' })).toBeInTheDocument();
    expect(screen.getByText('会員花子 さん')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '会員コード', level: 2 })).toBeInTheDocument();
  });

  it('取得完了までは読み込み中を表示する', () => {
    mockedHome.mockReturnValue(new Promise(() => {}));

    render(<MemberHomePage />);

    expect(screen.getByText('読み込み中...')).toBeInTheDocument();
  });

  it('取得に失敗したら失敗を名乗り、再試行で復帰できる', async () => {
    mockedHome.mockRejectedValueOnce(new Error('boom'));
    mockedHome.mockResolvedValueOnce({ member_code: '123456789012', display_name: '会員花子' });

    render(<MemberHomePage />);

    const region = await screen.findByRole('alert');
    expect(within(region).getByText('会員コードを取得できませんでした')).toBeInTheDocument();

    fireEvent.click(within(region).getByRole('button', { name: '再試行' }));

    expect(await screen.findByText('1234 5678 9012')).toBeInTheDocument();
  });

  it('会員コードの無い応答は「取れなかった」と同じ姿にする', async () => {
    // コードだけ欠けた見た目を出すと、読み取れないのが店舗側の問題に見える
    mockedHome.mockResolvedValue({ display_name: '会員花子' });

    render(<MemberHomePage />);

    expect(await screen.findByText('会員コードを取得できませんでした')).toBeInTheDocument();
    expect(screen.queryByText('読み込み中...')).not.toBeInTheDocument();
  });
});
