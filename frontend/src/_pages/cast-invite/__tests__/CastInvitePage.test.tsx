import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import CastInvitePage from '../ui/CastInvitePage';
import { castInvitationAcceptanceApi } from '@/entities/cast';

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castInvitationAcceptanceApi: {
      ...actual.castInvitationAcceptanceApi,
      view: jest.fn(),
      acceptAsExistingUser: jest.fn(),
      acceptAsNewUser: jest.fn(),
    },
  };
});

const mockedView = castInvitationAcceptanceApi.view as jest.Mock;
const mockedAcceptExisting = castInvitationAcceptanceApi.acceptAsExistingUser as jest.Mock;
const mockedAcceptNew = castInvitationAcceptanceApi.acceptAsNewUser as jest.Mock;

const NOT_FOUND = '招待リンクが見つかりません。URLをご確認ください。';

describe('CastInvitePage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    window.location.hash = '';
  });

  it('フラグメントのトークンで招待を照会する', async () => {
    // トークンはパスではなくフラグメントで届く（パスはアクセスログに残るため）
    window.location.hash = '#raw-invitation-token';
    mockedView.mockResolvedValue({ status: 'VALID', store_name: '銀座店', cast_name: '花子' });

    render(<CastInvitePage />);

    await screen.findByText('銀座店からの招待');
    expect(mockedView).toHaveBeenCalledWith('raw-invitation-token');
  });

  it('利用不可の招待では理由を表示し登録・ログインを案内しない', async () => {
    window.location.hash = '#withdrawn-invitation';
    mockedView.mockResolvedValue({ status: 'UNAVAILABLE', store_name: '銀座店' });

    render(<CastInvitePage />);

    await screen.findByText('この招待は利用できません。店舗の担当者にご確認ください。');
    expect(screen.queryByRole('button', { name: '新規登録して受諾' })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '既存アカウントでログイン' })
    ).not.toBeInTheDocument();
  });

  it('パーセント符号化されたトークンを復元して照会する', async () => {
    window.location.hash = '#a%2Fb';
    mockedView.mockResolvedValue({ status: 'VALID', store_name: '銀座店' });

    render(<CastInvitePage />);

    await screen.findByText('銀座店からの招待');
    expect(mockedView).toHaveBeenCalledWith('a/b');
  });

  it('フラグメントが無ければ照会せずに利用不可を出す', async () => {
    // トークンはパスに載らないので、フラグメント不在は招待を特定できない状態そのもの
    render(<CastInvitePage />);

    await screen.findByText(NOT_FOUND);
    expect(mockedView).not.toHaveBeenCalled();
  });

  it('壊れたパーセント符号化でも照会せずに利用不可を出す', async () => {
    window.location.hash = '#%';

    render(<CastInvitePage />);

    await screen.findByText(NOT_FOUND);
    expect(mockedView).not.toHaveBeenCalled();
  });

  it('受諾が済んだら完了画面からキャストのポータルへ進める', async () => {
    window.location.hash = '#raw-invitation-token';
    mockedView.mockResolvedValue({ status: 'VALID', store_name: '銀座店', cast_name: '花子' });
    mockedAcceptExisting.mockResolvedValue({ store_name: '銀座店' });

    render(<CastInvitePage />);

    fireEvent.click(await screen.findByRole('button', { name: '既存アカウントでログイン' }));
    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));

    expect(await screen.findByText('銀座店との連携が完了しました')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ポータルへ' })).toHaveAttribute('href', '/cast');
    expect(screen.queryByText(/準備中/)).not.toBeInTheDocument();
  });

  it('既存アカウントの受諾の応答の前に戻っても、成立したら完了画面を出す', async () => {
    window.location.hash = '#raw-invitation-token';
    mockedView.mockResolvedValue({ status: 'VALID', store_name: '銀座店', cast_name: '花子' });
    let respond!: (value: { store_name: string }) => void;
    mockedAcceptExisting.mockReturnValue(new Promise(resolve => (respond = resolve)));

    render(<CastInvitePage />);

    fireEvent.click(await screen.findByRole('button', { name: '既存アカウントでログイン' }));
    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'pass' } });
    fireEvent.click(screen.getByRole('button', { name: 'ログインして受諾する' }));
    await waitFor(() => expect(mockedAcceptExisting).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: '戻る' }));
    respond({ store_name: '銀座店' });

    expect(await screen.findByText('銀座店との連携が完了しました')).toBeInTheDocument();
  });

  it('新規登録の受諾の応答の前に戻っても、成立したら完了画面を出す', async () => {
    window.location.hash = '#raw-invitation-token';
    mockedView.mockResolvedValue({ status: 'VALID', store_name: '銀座店', cast_name: '花子' });
    let respond!: (value: { store_name: string }) => void;
    mockedAcceptNew.mockReturnValue(new Promise(resolve => (respond = resolve)));

    render(<CastInvitePage />);

    fireEvent.click(await screen.findByRole('button', { name: '新規登録して受諾' }));
    fireEvent.change(screen.getByLabelText('メールアドレス'), {
      target: { value: 'cast@kizuna.test' },
    });
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: '登録して受諾する' }));
    await waitFor(() => expect(mockedAcceptNew).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: '戻る' }));
    respond({ store_name: '銀座店' });

    expect(await screen.findByText('銀座店との連携が完了しました')).toBeInTheDocument();
  });
});
