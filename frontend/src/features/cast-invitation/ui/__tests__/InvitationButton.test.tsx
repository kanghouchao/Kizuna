import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { notify } from '@/shared/notify';
import { castApi } from '@/entities/cast';
import { InvitationButton } from '../InvitationButton';

jest.mock('@/entities/cast', () => ({
  castApi: { issueInvitation: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedCastApi = castApi as jest.Mocked<typeof castApi>;
const mockedNotify = notify as jest.Mocked<typeof notify>;

describe('キャスト招待の行内発行ボタン', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedCastApi.issueInvitation.mockResolvedValue({
      token: 'tok-1',
      expires_at: '2026-07-31T15:00:00Z',
    } as never);
  });

  it('連携済みなら何も描画しない', () => {
    const { container } = render(
      <InvitationButton castId="c1" status="LINKED" onIssued={jest.fn()} />
    );

    expect(container).toBeEmptyDOMElement();
  });

  it('未招待は「招待を発行」、招待中は「再発行」と表示する', () => {
    const { rerender } = render(
      <InvitationButton castId="c1" status="NOT_INVITED" onIssued={jest.fn()} />
    );

    expect(screen.getByRole('button', { name: '招待を発行' })).toBeInTheDocument();

    rerender(<InvitationButton castId="c1" status="INVITED" onIssued={jest.fn()} />);

    expect(screen.getByRole('button', { name: '再発行' })).toBeInTheDocument();
  });

  it('発行成功で token と有効期限を親へ渡す', async () => {
    const onIssued = jest.fn();
    render(<InvitationButton castId="c1" status="NOT_INVITED" onIssued={onIssued} />);

    fireEvent.click(screen.getByRole('button', { name: '招待を発行' }));

    await waitFor(() => expect(mockedCastApi.issueInvitation).toHaveBeenCalledWith('c1'));
    expect(onIssued).toHaveBeenCalledWith({
      token: 'tok-1',
      expiresAt: '2026-07-31T15:00:00Z',
    });
  });

  it('識別子の無いキャストには発行の要求を組まず、理由を名乗ること', async () => {
    // `?? ''` で素通しすると POST /store/casts//invitation が飛び、届いた先の 404 が
    // 「招待の発行に失敗しました」と見分けが付かなくなる
    const onIssued = jest.fn();
    render(<InvitationButton castId={undefined} status="NOT_INVITED" onIssued={onIssued} />);

    fireEvent.click(screen.getByRole('button', { name: '招待を発行' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith(
        expect.stringContaining('キャストの識別子が取得できていません')
      )
    );
    expect(mockedCastApi.issueInvitation).not.toHaveBeenCalled();
    expect(onIssued).not.toHaveBeenCalled();
  });

  it('発行失敗は親へ通知せず失敗トーストを出す', async () => {
    mockedCastApi.issueInvitation.mockRejectedValue(new Error('boom'));
    const onIssued = jest.fn();
    render(<InvitationButton castId="c1" status="NOT_INVITED" onIssued={onIssued} />);

    fireEvent.click(screen.getByRole('button', { name: '招待を発行' }));

    await waitFor(() =>
      expect(mockedNotify.error).toHaveBeenCalledWith('招待の発行に失敗しました')
    );
    expect(onIssued).not.toHaveBeenCalled();
  });
});
