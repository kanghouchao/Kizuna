'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import toast from 'react-hot-toast';
import { platformAuthApi, PlatformLoginRequest, resolvePlatformDestination } from '@/entities/user';
import {
  clearPlatformSession,
  getApiErrorMessage,
  setPlatformStore,
  startPlatformSession,
} from '@/shared/lib';

/** 統一ログイン動作。ログイン成功後はロールに応じて自動的に適切なコンソールへ遷移する（#324）。 */
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
      const { token, expires_at } = await platformAuthApi.login(data);
      // epoch millis を Date に変換する（旧 LoginForm の expires: expires_at は日数解釈の既知バグ）
      Cookies.set('token', token, { expires: new Date(expires_at) });

      const me = await platformAuthApi.me();
      const destination = resolvePlatformDestination(me.console);

      if (destination === 'central') {
        startPlatformSession(me.console, expires_at);
        router.push('/central/dashboard/');
        return;
      }

      if (destination === 'store') {
        const stores = await platformAuthApi.stores();
        if (stores.length === 0) {
          Cookies.remove('token');
          clearPlatformSession();
          toast.error('利用可能な店舗がありません。管理者にお問い合わせください');
          return;
        }
        startPlatformSession(me.console, expires_at);
        setPlatformStore(stores[0].id, expires_at);
        router.push('/tenant/dashboard/');
        return;
      }

      // console='none'（CAST/MEMBER・能力なし）のポータルは未提供
      Cookies.remove('token');
      clearPlatformSession();
      toast.error('この利用者種別のポータルは準備中です');
    } catch (error) {
      console.error('Platform login failed:', error);
      toast.error(
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
