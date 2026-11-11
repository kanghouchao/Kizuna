import { render } from '@testing-library/react';
import { useLandingPath } from '@/widgets/admin-shell';
import StaffLandingPage from '../ui/StaffLandingPage';

const replace = jest.fn();
jest.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }));
jest.mock('@/widgets/admin-shell', () => ({ useLandingPath: jest.fn() }));

const mockedLanding = useLandingPath as jest.MockedFunction<typeof useLandingPath>;

describe('従業員の着地', () => {
  beforeEach(() => jest.clearAllMocks());

  it('着地先が決まるまでは遷移しない', () => {
    mockedLanding.mockReturnValue(undefined);

    render(<StaffLandingPage />);

    expect(replace).not.toHaveBeenCalled();
  });

  it('着地先が決まったら履歴を残さずそこへ移る', () => {
    mockedLanding.mockReturnValue('/store/7/dashboard');

    render(<StaffLandingPage />);

    expect(replace).toHaveBeenCalledWith('/store/7/dashboard');
  });
});
