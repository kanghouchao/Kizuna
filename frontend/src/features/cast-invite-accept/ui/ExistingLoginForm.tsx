'use client';

import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  CastAcceptanceResponse,
  CastInvitationExistingAcceptRequest,
  castInvitationAcceptanceApi,
} from '@/entities/cast';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, getApiErrorMessage } from '@/shared/lib';

interface ExistingLoginFormProps {
  token: string;
  onSuccess: (response: CastAcceptanceResponse) => void;
  onBack: () => void;
}

/**
 * 既存アカウント（CAST 限定）での招待受諾。資格情報を受諾要求に添えて送り、バックエンドが照合から受諾まで
 * 済ませる。トークンを受け取らないので、訪問者が別のアカウントでログイン中でもそのセッションには触れない。
 */
export function ExistingLoginForm({ token, onSuccess, onBack }: ExistingLoginFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CastInvitationExistingAcceptRequest>({ defaultValues: { email: '', password: '' } });

  const submit = async (values: CastInvitationExistingAcceptRequest) => {
    try {
      onSuccess(await castInvitationAcceptanceApi.acceptAsExistingUser(token, values));
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'ログインまたは受諾に失敗しました'));
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
            htmlFor="invite-login-email"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            メールアドレス
          </label>
          <input
            id="invite-login-email"
            type="email"
            autoComplete="email"
            required
            className="auth-field__input"
            placeholder="example@mail.com"
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? 'invite-login-email-error' : undefined}
            {...register('email', {
              required: 'メールアドレスを入力してください',
              pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.email && (
          <p id="invite-login-email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
        <div className="auth-field">
          <label
            htmlFor="invite-login-password"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            パスワード
          </label>
          <input
            id="invite-login-password"
            type="password"
            autoComplete="current-password"
            required
            className="auth-field__input"
            placeholder="パスワードを入力"
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'invite-login-password-error' : undefined}
            {...register('password', { required: 'パスワードを入力してください' })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.password && (
          <p id="invite-login-password-error" className="auth-field__error">
            {errors.password.message}
          </p>
        )}
      </div>

      <div className="pt-2 space-y-3">
        <button type="submit" disabled={isSubmitting} className="auth-btn">
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2.5">
              <span className="auth-spinner" />
              ログイン中...
            </span>
          ) : (
            'ログインして受諾する'
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
