import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemberAccountPage } from '../MemberAccountPage';

const mockLogout = jest.fn();
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: mockLogout }),
}));

describe('MemberAccountPage', () => {
  it('設定の一覧から通知の設定と LINE 連携へ入れる', () => {
    render(<MemberAccountPage />);

    const settings = screen.getByRole('list', { name: '設定' });
    expect(within(settings).getByRole('link', { name: '通知の設定' })).toHaveAttribute(
      'href',
      '/member/notification-settings'
    );
    expect(within(settings).getByRole('link', { name: 'LINE連携' })).toHaveAttribute(
      'href',
      '/member/account/line'
    );
  });

  it('退会の入口は設定の一覧に混ぜず、最下部のリンクに置く', () => {
    render(<MemberAccountPage />);

    const settings = screen.getByRole('list', { name: '設定' });
    expect(within(settings).queryByRole('link', { name: '退会する' })).not.toBeInTheDocument();
    const withdrawal = screen.getByRole('link', { name: '退会する' });
    expect(withdrawal).toHaveAttribute('href', '/member/withdrawal');
    // 文書順でログアウトより後ろにある（誤操作しにくい最下部）
    expect(
      screen.getByRole('button', { name: 'ログアウト' }).compareDocumentPosition(withdrawal) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it('ログアウトは、終わるまで続けて押しても 1 回だけ送る', async () => {
    let finish: () => void = () => {};
    mockLogout.mockReturnValueOnce(new Promise<void>(resolve => (finish = resolve)));
    render(<MemberAccountPage />);

    const button = screen.getByRole('button', { name: 'ログアウト' });
    act(() => {
      fireEvent.click(button);
      fireEvent.click(button);
    });

    await waitFor(() => expect(button).toBeDisabled());
    expect(mockLogout).toHaveBeenCalledTimes(1);

    await act(async () => finish());
    await waitFor(() => expect(button).toBeEnabled());
  });
});
