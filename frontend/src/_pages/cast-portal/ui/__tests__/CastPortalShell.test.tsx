import { render, screen } from '@testing-library/react';
import { CastPortalShell } from '../CastPortalShell';
import { redirectToLogin } from '@/shared/lib';

let mockPathname = '/cast/schedule';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
}));

jest.mock('@/shared/lib', () => ({
  ...jest.requireActual('@/shared/lib'),
  redirectToLogin: jest.fn(),
}));

const mockedRedirect = redirectToLogin as jest.Mock;

describe('CastPortalShell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/cast/schedule';
  });

  // 未ログイン・別種別の差し戻しは API 応答（401 / 403）に任せ、シェル自身は本人を判定しない
  it('セッションを判定せず、children と本人ポータルのタブを表示する', async () => {
    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    expect(await screen.findByText('子要素')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /スケジュール/ })).toHaveAttribute(
      'href',
      '/cast/schedule'
    );
    expect(screen.getByRole('link', { name: /希望提出/ })).toHaveAttribute(
      'href',
      '/cast/requests'
    );
    expect(screen.getByRole('link', { name: /アカウント/ })).toHaveAttribute(
      'href',
      '/cast/account'
    );
    expect(screen.getByRole('link', { name: '報酬明細' })).toHaveAttribute(
      'href',
      '/cast/remunerations'
    );
    expect(mockedRedirect).not.toHaveBeenCalled();
  });

  it('現在のパスに一致するタブを aria-current=page でハイライトする', async () => {
    mockPathname = '/cast/account';
    render(
      <CastPortalShell>
        <p>子要素</p>
      </CastPortalShell>
    );

    await screen.findByText('子要素');

    expect(screen.getByRole('link', { name: /アカウント/ })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('link', { name: /スケジュール/ })).not.toHaveAttribute('aria-current');
  });
});
