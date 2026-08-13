import { render, screen } from '@testing-library/react';
import CastInvitePage from '../ui/CastInvitePage';
import { castInvitationAcceptanceApi } from '@/entities/cast';

jest.mock('@/entities/cast', () => {
  const actual = jest.requireActual('@/entities/cast');
  return {
    ...actual,
    castInvitationAcceptanceApi: {
      ...actual.castInvitationAcceptanceApi,
      view: jest.fn(),
    },
  };
});

const mockedView = castInvitationAcceptanceApi.view as jest.Mock;

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
});
