'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import { platformAuthApi, PlatformLoginRequest } from '@/entities/user';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, getApiErrorMessage } from '@/shared/lib';
import { completePlatformLogin } from '../model/completePlatformLogin';

/** 統一ログイン動作。ログイン成功後はロールに応じて自動的に適切なコンソールへ遷移する。 */
export default function PlatformLoginForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PlatformLoginRequest>({ defaultValues: { email: '', password: '' } });

  const onSubmit = async (data: PlatformLoginRequest) => {
    Cookies.remove('token');
    try {
      const completion = await completePlatformLogin(await platformAuthApi.login(data));
      if (completion.status === 'unsupported') {
        notify.error('この利用者種別のポータルは準備中です');
        return;
      }
      router.push(completion.path);
    } catch (error) {
      console.error('Platform login failed:', error);
      notify.error(
        getApiErrorMessage(error, 'ログインに失敗しました。しばらくしてから再度お試しください')
      );
    }
  };

  return (
    // noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
    // 永久に描かれない。type="email" の執行もここで止まるため、下の pattern が引き継ぐ。
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-7" noValidate>
      {/* メールアドレス */}
      <div className="auth-field">
        <label
          htmlFor="email"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          メールアドレス
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          required
          className="auth-field__input"
          placeholder="example@mail.com"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
          {...register('email', {
            required: 'メールアドレスを入力してください',
            pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
          })}
        />
        <span className="auth-field__accent" />
        {errors.email && (
          <p id="email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      {/* パスワード */}
      <div className="auth-field">
        <label
          htmlFor="password"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          パスワード
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          required
          className="auth-field__input"
          placeholder="パスワードを入力"
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'password-error' : undefined}
          {...register('password', { required: 'パスワードを入力してください' })}
        />
        <span className="auth-field__accent" />
        {errors.password && (
          <p id="password-error" className="auth-field__error">
            {errors.password.message}
          </p>
        )}
      </div>

      {/* ログインボタン */}
      <div className="pt-2">
        <button type="submit" disabled={isSubmitting} className="auth-btn">
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2.5">
              <span className="auth-spinner" />
              ログイン中...
            </span>
          ) : (
            'ログイン'
          )}
        </button>
      </div>
    </form>
  );
}
