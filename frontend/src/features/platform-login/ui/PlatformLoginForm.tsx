'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import toast from 'react-hot-toast';
import { platformAuthApi, PlatformLoginRequest, resolvePlatformDestination } from '@/entities/user';
import {
  clearPlatformSession,
  getApiErrorMessage,
  startPlatformSession,
  storeSelectPath,
} from '@/shared/lib';

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
      const { token, expires_at } = await platformAuthApi.login(data);
      // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
      Cookies.set('token', token, { expires: new Date(expires_at) });

      const me = await platformAuthApi.me();
      const destination = resolvePlatformDestination(me.console);

      if (destination === 'platform') {
        startPlatformSession(me.console, expires_at);
        router.push('/platform/dashboard/');
        return;
      }

      if (destination === 'store') {
        // 着地方針（1店舗=自動転送 / N店舗=選択画面 / 0店舗=案内表示）は StoreSelectPage 一箇所に集約する。
        // ログインフォームは無条件に選択画面へ渡し、stores[0] 無条件遷移（複数店舗ユーザーが選択画面へ
        // 到達できない矛盾）を解消する。
        startPlatformSession(me.console, expires_at);
        router.push(storeSelectPath());
        return;
      }

      // destination='unsupported'（console='none' — CAST または MEMBER）。両者は console だけでは
      // 区別できないため、既に取得済みの user_type で分岐する（#328）。
      if (me.user_type === 'CAST') {
        startPlatformSession('cast', expires_at);
        router.push('/cast/schedule/');
        return;
      }

      // MEMBER 等: ポータル未提供
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
