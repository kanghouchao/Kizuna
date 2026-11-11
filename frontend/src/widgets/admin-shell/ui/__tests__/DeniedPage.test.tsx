import { render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import type { useMe } from '@/entities/user';
import { notify } from '@/shared/notify';
import { useLandingPath } from '../../model/landing';
import { DeniedPage, LeaveToLanding, PermissionGate } from '../DeniedPage';

const replace = jest.fn();
let mockPathname = '/admin/staff';
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ replace }),
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

jest.mock('../../model/landing', () => ({ useLandingPath: jest.fn() }));
jest.mock('@/entities/user/model/MeContext', () => ({ useMe: jest.fn() }));

const mockedLanding = useLandingPath as jest.MockedFunction<typeof useLandingPath>;
const mockedUseMe = jest.requireMock<{ useMe: jest.MockedFunction<typeof useMe> }>(
  '@/entities/user/model/MeContext'
).useMe;

const meWith = (permissions: string[]) =>
  ({
    me: { user_type: 'STAFF', permissions, email: 'a@kizuna.test', display_name: 'A' },
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  }) as unknown as ReturnType<typeof useMe>;

describe('LeaveToLanding（開けない画面から着地へ戻す）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/admin/staff';
    mockedLanding.mockReturnValue('/admin/dashboard');
  });

  it('着地へ置き換え、警告を一度だけ出す（StrictMode の二重実行でも）', () => {
    render(
      <StrictMode>
        <LeaveToLanding message="この店舗の画面は開けません" />
      </StrictMode>
    );

    expect(replace).toHaveBeenCalledTimes(1);
    expect(replace).toHaveBeenCalledWith('/admin/dashboard');
    expect(notify.warning).toHaveBeenCalledTimes(1);
    expect(notify.warning).toHaveBeenCalledWith('この店舗の画面は開けません');
  });

  it('着地が決まるまでは移らない', () => {
    mockedLanding.mockReturnValue(undefined);

    render(<LeaveToLanding message="この店舗の画面は開けません" />);

    expect(replace).not.toHaveBeenCalled();
    expect(notify.warning).not.toHaveBeenCalled();
  });

  it('開けない画面が着地そのものなら移らず、その場で開けないと示す', () => {
    mockPathname = '/admin/dashboard';

    render(<LeaveToLanding message="この画面を開く権限がありません" />);

    expect(replace).not.toHaveBeenCalled();
    expect(notify.warning).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('この画面を開く権限がありません');
  });
});

describe('DeniedPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/admin/staff';
    mockedLanding.mockReturnValue('/admin/dashboard');
  });

  it('権限が無いと警告して着地へ戻す', () => {
    render(<DeniedPage />);

    expect(replace).toHaveBeenCalledWith('/admin/dashboard');
    expect(notify.warning).toHaveBeenCalledWith('この画面を開く権限がありません');
  });
});

describe('PermissionGate（ページ全体の権限）', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = '/admin/staff';
    mockedLanding.mockReturnValue('/admin/dashboard');
  });

  it('本人情報の取得中は中身も移動も出さない', () => {
    mockedUseMe.mockReturnValue({
      me: null,
      failure: null,
      reload: jest.fn(),
    } as unknown as ReturnType<typeof useMe>);

    render(
      <PermissionGate codes={['STAFF_MANAGE']}>
        <p>本文</p>
      </PermissionGate>
    );

    expect(screen.queryByText('本文')).not.toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it('全ての権限を持てば中身を描く', () => {
    mockedUseMe.mockReturnValue(meWith(['CUSTOMER_MANAGE', 'CUSTOMER_MERGE']));

    render(
      <PermissionGate codes={['CUSTOMER_MANAGE', 'CUSTOMER_MERGE']}>
        <p>本文</p>
      </PermissionGate>
    );

    expect(screen.getByText('本文')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it('一つでも欠ければ中身を描かず、警告して着地へ戻す', () => {
    mockedUseMe.mockReturnValue(meWith(['CUSTOMER_MANAGE']));

    render(
      <PermissionGate codes={['CUSTOMER_MANAGE', 'CUSTOMER_MERGE']}>
        <p>本文</p>
      </PermissionGate>
    );

    expect(screen.queryByText('本文')).not.toBeInTheDocument();
    expect(replace).toHaveBeenCalledWith('/admin/dashboard');
    expect(notify.warning).toHaveBeenCalledWith('この画面を開く権限がありません');
  });
});
