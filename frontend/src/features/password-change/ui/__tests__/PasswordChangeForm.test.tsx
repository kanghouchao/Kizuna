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

  it('確認用パスワードが一致しない場合は、その欄の傍に文言を出し API を呼ばないこと', async () => {
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    fireEvent.change(current, { target: { value: 'oldpass123' } });
    fireEvent.change(next, { target: { value: 'newpass123' } });
    fireEvent.change(confirm, { target: { value: 'other12345' } });
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));

    const message = await screen.findByText('新しいパスワードが一致しません');
    expect(mockedToastError).not.toHaveBeenCalled();
    expect(mockedChangePassword).not.toHaveBeenCalled();
    expect(mockLogout).not.toHaveBeenCalled();
    // 「入力の傍」は視覚的な近さだけでなく、読み上げが欄と指摘を結べることまで含む
    expect(confirm).toHaveAttribute('aria-invalid', 'true');
    expect(confirm.getAttribute('aria-describedby')).toContain(message.id);
  });

  it('未入力のまま送信すると各欄の傍に必須の文言が出て、ボタンは無効化されないこと', async () => {
    render(<PasswordChangeForm />);
    const submitButton = screen.getByRole('button', { name: 'パスワードを変更する' });

    fireEvent.click(submitButton);

    expect(await screen.findByText('現在のパスワードを入力してください')).toBeInTheDocument();
    expect(screen.getByText('新しいパスワードを入力してください')).toBeInTheDocument();
    expect(screen.getByText('確認用のパスワードを入力してください')).toBeInTheDocument();
    expect(mockedChangePassword).not.toHaveBeenCalled();
    // 押させて指摘を出す方が、押せない理由の分からないボタンより情報が多い
    expect(submitButton).toBeEnabled();
  });

  it('8 文字未満は新しいパスワードの欄が文言で拒むこと', async () => {
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    fireEvent.change(current, { target: { value: 'oldpass123' } });
    fireEvent.change(next, { target: { value: 'short7' } });
    fireEvent.change(confirm, { target: { value: 'short7' } });
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));

    expect(
      await screen.findByText('新しいパスワードは8文字以上で入力してください')
    ).toBeInTheDocument();
    expect(mockedChangePassword).not.toHaveBeenCalled();
  });

  // 成功後はログアウトの遷移が進むので押させないが、失敗したときの画面はまだ立っている。
  // 押し直せないと、リロード以外に回復手段が無くなる
  it('変更に失敗したらボタンを押し直せること', async () => {
    mockedChangePassword.mockRejectedValue({ response: { status: 400 } });
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    fireEvent.change(current, { target: { value: 'oldpass123' } });
    fireEvent.change(next, { target: { value: 'newpass123' } });
    fireEvent.change(confirm, { target: { value: 'newpass123' } });
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを変更する' }));

    await waitFor(() => expect(mockedToastError).toHaveBeenCalled());
    expect(mockLogout).not.toHaveBeenCalled();
    expect(await screen.findByRole('button', { name: 'パスワードを変更する' })).toBeEnabled();
  });

  it('type / required は維持され、執行しなくなった minLength は外れていること', () => {
    const { container } = render(<PasswordChangeForm />);
    const { current, next, confirm } = fields(container);

    for (const input of [current, next, confirm]) {
      expect(input.type).toBe('password');
      // 執行は react-hook-form の規則、属性は必須であることを支援技術へ伝えるヒント
      expect(input.required).toBe(true);
    }
    // noValidate の下では何も執行せず何も告知しないため、残すと守っているように誤読される
    expect(next.hasAttribute('minlength')).toBe(false);
    expect(confirm.hasAttribute('minlength')).toBe(false);
  });
});
