import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { toast } from 'react-hot-toast';
import { PasswordChangeForm } from '../PasswordChangeForm';
import { platformAuthApi } from '@/entities/user';

const mockLogout = jest.fn();

jest.mock('react-hot-toast', () => ({
  toast: { success: jest.fn(), error: jest.fn() },
}));

jest.mock('@/entities/user', () => ({
  platformAuthApi: { changePassword: jest.fn() },
  useAuth: () => ({ logout: mockLogout }),
}));

const mockedChangePassword = platformAuthApi.changePassword as jest.Mock;
const mockedToastError = toast.error as jest.Mock;

/** autoComplete 属性でフィールドを特定する（ラベル関連付けの有無に依存しない）。 */
function fields(container: HTMLElement) {
  const newPasswords = container.querySelectorAll<HTMLInputElement>(
    'input[autocomplete="new-password"]'
  );
  return {
    current: container.querySelector<HTMLInputElement>('input[autocomplete="current-password"]')!,
    next: newPasswords[0],
    confirm: newPasswords[1],
  };
}

describe('パスワード変更フォーム', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('入力が揃うと current_password / new_password のみで API を呼びログアウトすること', async () => {
    mockedChangePassword.mockResolvedValue(undefined);
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    fireEvent.change(current, { target: { value: 'oldpass123' } });
    fireEvent.change(next, { target: { value: 'newpass123' } });
    fireEvent.change(confirm, { target: { value: 'newpass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));

    await waitFor(() => expect(mockedChangePassword).toHaveBeenCalledTimes(1));
    const body = mockedChangePassword.mock.calls[0][0] as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['current_password', 'new_password']);
    expect(body).toEqual({ current_password: 'oldpass123', new_password: 'newpass123' });
    await waitFor(() => expect(mockLogout).toHaveBeenCalledTimes(1));
  });

  it('確認用パスワードが一致しない場合は API を呼ばないこと', async () => {
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    fireEvent.change(current, { target: { value: 'oldpass123' } });
    fireEvent.change(next, { target: { value: 'newpass123' } });
    fireEvent.change(confirm, { target: { value: 'other12345' } });
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));

    await waitFor(() =>
      expect(mockedToastError).toHaveBeenCalledWith('新しいパスワードが一致しません')
    );
    expect(mockedChangePassword).not.toHaveBeenCalled();
    expect(mockLogout).not.toHaveBeenCalled();
  });

  it('ブラウザ側の入力制約（type / required / minLength）が維持されていること', () => {
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    for (const input of [current, next, confirm]) {
      expect(input.type).toBe('password');
      expect(input.required).toBe(true);
    }
    expect(next.minLength).toBe(8);
    expect(confirm.minLength).toBe(8);
  });
});
