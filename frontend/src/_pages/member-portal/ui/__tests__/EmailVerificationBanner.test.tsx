import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { EmailVerificationBanner } from '../EmailVerificationBanner';
import { useMe, type MeResponse } from '@/entities/user';
import { memberApi } from '@/entities/member';
import { notify } from '@/shared/notify';

jest.mock('@/entities/user/model/MeContext', () => ({
  MeProvider: ({ children }: { children: React.ReactNode }) => children,
  useMe: jest.fn(),
}));

jest.mock('@/entities/member', () => ({
  memberApi: { resendEmailVerification: jest.fn() },
}));

jest.mock('@/shared/notify', () => ({
  notify: { success: jest.fn(), error: jest.fn(), warning: jest.fn() },
}));

const mockedUseMe = useMe as jest.Mock;

function rejected(status: number, error: string) {
  const headers = new AxiosHeaders();
  return new AxiosError('failed', undefined, { headers }, undefined, {
    status,
    statusText: '',
    headers,
    config: { headers },
    data: { error },
  });
}

function member(overrides: Partial<MeResponse>): MeResponse {
  return {
    email: 'member@kizuna.test',
    display_name: '会員',
    user_type: 'MEMBER',
    permissions: [],
    line_linked: false,
    email_verified: false,
    ...overrides,
  };
}

function renderBanner(me: MeResponse | null) {
  mockedUseMe.mockReturnValue({
    me,
    isLoading: me === null,
    failure: null,
    reload: jest.fn(),
    setMe: jest.fn(),
  });
  render(<EmailVerificationBanner />);
}

describe('EmailVerificationBanner', () => {
  it('未検証の会員には確認メールを送った宛先つきの案内を出す', () => {
    renderBanner(member({ email_verified: false }));

    const banner = screen.getByRole('status');
    expect(banner).toHaveTextContent('メールアドレスの確認が済んでいません');
    expect(banner).toHaveTextContent('member@kizuna.testに確認メールを送りました');
  });

  it('検証済みの会員には案内を出さない', () => {
    renderBanner(member({ email_verified: true }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('本人資料を読めるまでは案内を出さない', () => {
    renderBanner(null);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('会員以外のセッションには案内を出さない', () => {
    renderBanner(member({ user_type: 'STAFF', email_verified: false }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  describe('確認メールの再送', () => {
    beforeEach(() => {
      jest.mocked(memberApi.resendEmailVerification).mockReset();
      jest.mocked(notify.success).mockClear();
      jest.mocked(notify.error).mockClear();
    });

    it('再送できたら送ったことを伝え、送信中は押せない', async () => {
      let resolve: () => void = () => {};
      jest
        .mocked(memberApi.resendEmailVerification)
        .mockReturnValue(new Promise<void>(r => (resolve = r)));
      renderBanner(member({ email_verified: false }));

      const button = screen.getByRole('button', { name: '確認メールを再送する' });
      fireEvent.click(button);
      expect(button).toBeDisabled();
      resolve();

      await waitFor(() =>
        expect(notify.success).toHaveBeenCalledWith('member@kizuna.testに確認メールを送りました')
      );
      expect(button).toBeEnabled();
      expect(memberApi.resendEmailVerification).toHaveBeenCalledTimes(1);
    });

    it.each([
      [429, 'しばらく時間をおいてからお試しください'],
      [400, 'メールアドレスは確認済みです。画面を再読み込みしてください'],
    ])('%i ならサーバの文言をそのまま伝える', async (status, error) => {
      jest.mocked(memberApi.resendEmailVerification).mockRejectedValue(rejected(status, error));
      renderBanner(member({ email_verified: false }));

      fireEvent.click(screen.getByRole('button', { name: '確認メールを再送する' }));

      await waitFor(() => expect(notify.error).toHaveBeenCalledWith(error));
      expect(notify.success).not.toHaveBeenCalled();
    });
  });
});
