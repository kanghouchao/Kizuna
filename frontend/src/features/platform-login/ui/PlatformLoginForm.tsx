'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import { platformAuthApi, PlatformLoginRequest } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { completePlatformLogin } from '../model/completePlatformLogin';

/** 統一ログイン動作。ログイン成功後はロールに応じて自動的に適切なコンソールへ遷移する。 */
export default function PlatformLoginForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
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
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-7">
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
          {...register('email', { required: true })}
        />
        <span className="auth-field__accent" />
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
          {...register('password', { required: true })}
        />
        <span className="auth-field__accent" />
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
