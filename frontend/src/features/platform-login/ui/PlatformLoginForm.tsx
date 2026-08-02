'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import toast from 'react-hot-toast';
import { platformAuthApi, PlatformLoginRequest, resolvePlatformDestination } from '@/entities/user';
import { clearMeCache } from '@/shared/api';
import {
  clearPlatformSession,
  getApiErrorMessage,
  startPlatformSession,
  storeEntryPath,
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
    // 前セッションの token と一緒に me キャッシュも消す。ここでログインが失敗すると
    // token を失った端末に前利用者の個人情報だけが残るため
    Cookies.remove('token');
    clearMeCache();
    try {
      const { token, expires_at } = await platformAuthApi.login(data);
      // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
      Cookies.set('token', token ?? '', { expires: new Date(expires_at) });

      const me = await platformAuthApi.me();
      const activeConsole = me.console ?? 'none';
      const destination = resolvePlatformDestination(activeConsole);

      if (destination === 'platform') {
        startPlatformSession(activeConsole, expires_at);
        router.push('/platform/dashboard/');
        return;
      }

      if (destination === 'store') {
        // 着地方針（授権店舗の選択とメニュー由来の着地先解決）は StoreEntryPage 一箇所に集約する。
        // ログインフォームは無条件に入口へ渡し、店舗の解決には関与しない。
        startPlatformSession(activeConsole, expires_at);
        router.push(storeEntryPath());
        return;
      }

      // destination='unsupported'（console='none' — CAST または MEMBER）。両者は console だけでは
      // 区別できないため、既に取得済みの user_type で分岐する。
      if (me.user_type === 'CAST') {
        startPlatformSession('cast', expires_at);
        router.push('/cast/schedule/');
        return;
      }

      if (me.user_type === 'MEMBER') {
        startPlatformSession('member', expires_at);
        router.push('/member/');
        return;
      }

      // 想定外の user_type: 着地先が無いためセッションを破棄する（直前の me() が
      // キャッシュした個人情報も一緒に消す）
      Cookies.remove('token');
      clearMeCache();
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
