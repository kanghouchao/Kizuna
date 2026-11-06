import { fireEvent, render, screen } from '@testing-library/react';
import { MemberAccountPage } from '../MemberAccountPage';

const mockLogout = jest.fn();
jest.mock('@/entities/user', () => ({
  useAuth: () => ({ logout: mockLogout }),
}));

describe('MemberAccountPage', () => {
  it('通知の設定と退会へ入れる', () => {
    render(<MemberAccountPage />);

    expect(screen.getByRole('link', { name: '通知の設定' })).toHaveAttribute(
      'href',
      '/member/notification-settings'
    );
    expect(screen.getByRole('link', { name: '退会する' })).toHaveAttribute(
      'href',
      '/member/withdrawal'
    );
  });

  it('ログアウトできる', () => {
    render(<MemberAccountPage />);

    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));

    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
