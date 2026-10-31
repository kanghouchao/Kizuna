import { render, screen } from '@testing-library/react';
import { MemberPortalShell } from '../MemberPortalShell';
import { redirectToLogin, rememberMemberReturnPath } from '@/shared/lib';

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
  rememberMemberReturnPath: jest.fn(),
}));

describe('MemberPortalShell', () => {
  // 未ログインの差し戻しと戻り先の預けは全域の 401 経路が担い、シェル自身は本人を判定しない
  it('セッションを判定せず children を表示する', () => {
    render(
      <MemberPortalShell>
        <p>子要素</p>
      </MemberPortalShell>
    );

    expect(screen.getByText('子要素')).toBeInTheDocument();
    expect(redirectToLogin).not.toHaveBeenCalled();
    expect(rememberMemberReturnPath).not.toHaveBeenCalled();
  });
});
