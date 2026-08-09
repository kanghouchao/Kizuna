'use client';

import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import { CastAcceptanceResponse, castInvitationAcceptanceApi } from '@/entities/cast';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, getApiErrorMessage } from '@/shared/lib';

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
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    defaultValues: { email: '', password: '', display_name: initialDisplayName },
  });

  const submit = async (values: RegisterFormValues) => {
    try {
      const response = await castInvitationAcceptanceApi.acceptAsNewUser(token, values);
      onSuccess(response);
    } catch (error) {
      notify.error(getApiErrorMessage(error, '登録に失敗しました'));
    }
  };

  return (
    // noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
    // 永久に描かれない。type="email" の執行もここで止まるため、下の pattern が引き継ぐ。
    <form onSubmit={handleSubmit(submit)} className="space-y-7" noValidate>
      {/* 文言は .auth-field の外に置く。中に入れると下線（bottom:0 の絶対配置）が
          伸びた入れ物の底へ移り、文言を貫いて描かれる */}
      <div>
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
            maxLength={127}
            className="auth-field__input"
            placeholder="example@mail.com"
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? 'invite-email-error' : undefined}
            {...register('email', {
              required: 'メールアドレスを入力してください',
              pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.email && (
          <p id="invite-email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
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
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'invite-password-error' : undefined}
            {...register('password', { required: 'パスワードを入力してください' })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.password && (
          <p id="invite-password-error" className="auth-field__error">
            {errors.password.message}
          </p>
        )}
      </div>

      <div>
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
            aria-invalid={!!errors.display_name}
            aria-describedby={errors.display_name ? 'invite-display-name-error' : undefined}
            {...register('display_name', { required: '表示名を入力してください' })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.display_name && (
          <p id="invite-display-name-error" className="auth-field__error">
            {errors.display_name.message}
          </p>
        )}
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
