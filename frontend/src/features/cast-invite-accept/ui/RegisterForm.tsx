'use client';

import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastAcceptanceResponse, castInvitationAcceptanceApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';

interface RegisterFormProps {
  token: string;
  /** 表示名の初期値（档案名）。変更可。 */
  initialDisplayName: string;
  onSuccess: (response: CastAcceptanceResponse) => void;
  onBack: () => void;
}

interface RegisterFormValues {
  email: string;
  password: string;
  display_name: string;
}

/** 招待の新規登録受諾フォーム（email/password/表示名の3項目のみ。裁定7）。 */
export function RegisterForm({ token, initialDisplayName, onSuccess, onBack }: RegisterFormProps) {
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<RegisterFormValues>({
    defaultValues: { email: '', password: '', display_name: initialDisplayName },
  });

  const submit = async (values: RegisterFormValues) => {
    try {
      const response = await castInvitationAcceptanceApi.acceptAsNewUser(token, values);
      onSuccess(response);
    } catch (error) {
      toast.error(getApiErrorMessage(error, '登録に失敗しました'));
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-7">
      <div className="auth-field">
        <label
          htmlFor="invite-email"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          メールアドレス
        </label>
        <input
          id="invite-email"
          type="email"
          autoComplete="email"
          required
          className="auth-field__input"
          placeholder="example@mail.com"
          {...register('email', { required: true })}
        />
        <span className="auth-field__accent" />
      </div>

      <div className="auth-field">
        <label
          htmlFor="invite-password"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          パスワード
        </label>
        <input
          id="invite-password"
          type="password"
          autoComplete="new-password"
          required
          className="auth-field__input"
          placeholder="パスワードを入力"
          {...register('password', { required: true })}
        />
        <span className="auth-field__accent" />
      </div>

      <div className="auth-field">
        <label
          htmlFor="invite-display-name"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          表示名
        </label>
        <input
          id="invite-display-name"
          type="text"
          required
          className="auth-field__input"
          {...register('display_name', { required: true })}
        />
        <span className="auth-field__accent" />
      </div>

      <div className="pt-2 space-y-3">
        <button type="submit" disabled={isSubmitting} className="auth-btn">
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2.5">
              <span className="auth-spinner" />
              登録中...
            </span>
          ) : (
            '登録して受諾する'
          )}
        </button>
        <button
          type="button"
          onClick={onBack}
          className="w-full text-center text-xs text-[#9a958e] hover:text-[#7c3aed] focus:outline-none focus:underline"
        >
          戻る
        </button>
      </div>
    </form>
  );
}
