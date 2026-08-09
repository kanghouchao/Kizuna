'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import { memberApi, MemberRegisterRequest } from '@/entities/member';
import { platformAuthApi } from '@/entities/user';
import {
  EMAIL_PATTERN,
  EMAIL_PATTERN_MESSAGE,
  getApiErrorMessage,
  startPlatformSession,
} from '@/shared/lib';

/**
 * 会員の新規登録フォーム（メール + パスワード + 表示名の 3 項目）。
 * 登録成功後はそのままログインして会員ポータルのホーム（会員コード表示）へ遷移する。
 */
export function MemberRegisterForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<MemberRegisterRequest>({
    defaultValues: { email: '', password: '', display_name: '' },
  });

  const submit = async (values: MemberRegisterRequest) => {
    try {
      await memberApi.register(values);
    } catch (error) {
      notify.error(getApiErrorMessage(error, '登録に失敗しました'));
      return;
    }
    try {
      // 登録直後のログインは本フローがセッションを扱うため、グローバルな 401 リダイレクトから除外する。
      const { token, expires_at } = await platformAuthApi.login(
        { email: values.email, password: values.password },
        { skipAuthRedirect: true }
      );
      // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
      Cookies.set('token', token ?? '', { expires: new Date(expires_at) });
      startPlatformSession('member', expires_at);
      router.push('/member/');
    } catch {
      // 登録自体は完了している。自動ログインだけが失敗した稀な場合はログイン画面から再開させる。
      notify.success('登録が完了しました。ログインしてください');
      router.push('/platform/login');
    }
  };

  return (
    // noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
    // 永久に描かれない。type="email" の執行もここで止まるため、下の pattern が引き継ぐ。
    <form onSubmit={handleSubmit(submit)} className="space-y-7" noValidate>
      <div className="auth-field">
        <label
          htmlFor="register-email"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          メールアドレス
        </label>
        <input
          id="register-email"
          type="email"
          autoComplete="email"
          required
          maxLength={127}
          className="auth-field__input"
          placeholder="example@mail.com"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'register-email-error' : undefined}
          {...register('email', {
            required: 'メールアドレスを入力してください',
            pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
            maxLength: { value: 127, message: 'メールアドレスは127文字以内で入力してください' },
          })}
        />
        <span className="auth-field__accent" />
        {errors.email && (
          <p id="register-email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label
          htmlFor="register-password"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          パスワード
        </label>
        {/* 原生 minLength は外す。noValidate の下では何も執行せず何も告知しないため、
            残すと「まだ何かを守っている」と読者を誤解させるだけになる。 */}
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          required
          className="auth-field__input"
          placeholder="8文字以上のパスワード"
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'register-password-error' : undefined}
          {...register('password', {
            required: 'パスワードを入力してください',
            minLength: { value: 8, message: 'パスワードは8文字以上で入力してください' },
          })}
        />
        <span className="auth-field__accent" />
        {errors.password && (
          <p id="register-password-error" className="auth-field__error">
            {errors.password.message}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label
          htmlFor="register-display-name"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          表示名
        </label>
        <input
          id="register-display-name"
          type="text"
          required
          maxLength={150}
          className="auth-field__input"
          placeholder="表示名を入力"
          aria-invalid={!!errors.display_name}
          aria-describedby={errors.display_name ? 'register-display-name-error' : undefined}
          {...register('display_name', {
            required: '表示名を入力してください',
            maxLength: { value: 150, message: '表示名は150文字以内で入力してください' },
          })}
        />
        <span className="auth-field__accent" />
        {errors.display_name && (
          <p id="register-display-name-error" className="auth-field__error">
            {errors.display_name.message}
          </p>
        )}
      </div>

      <div className="pt-2">
        <button type="submit" disabled={isSubmitting} className="auth-btn">
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2.5">
              <span className="auth-spinner" />
              登録中...
            </span>
          ) : (
            '登録する'
          )}
        </button>
      </div>
    </form>
  );
}
