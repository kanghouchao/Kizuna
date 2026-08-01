'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { toast } from 'react-hot-toast';
import { memberApi, MemberRegisterRequest } from '@/entities/member';
import { platformAuthApi } from '@/entities/user';
import { getApiErrorMessage, startPlatformSession } from '@/shared/lib';

/**
 * 会員の新規登録フォーム（メール + パスワード + 表示名の 3 項目）。
 * 登録成功後はそのままログインして会員ポータルのホーム（会員コード表示）へ遷移する。
 */
export function MemberRegisterForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<MemberRegisterRequest>({
    defaultValues: { email: '', password: '', display_name: '' },
  });

  const submit = async (values: MemberRegisterRequest) => {
    try {
      await memberApi.register(values);
    } catch (error) {
      toast.error(getApiErrorMessage(error, '登録に失敗しました'));
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
      toast.error('登録が完了しました。ログインしてください');
      router.push('/platform/login');
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-7">
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
          className="auth-field__input"
          placeholder="example@mail.com"
          {...register('email', { required: true })}
        />
        <span className="auth-field__accent" />
      </div>

      <div className="auth-field">
        <label
          htmlFor="register-password"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          パスワード
        </label>
        <input
          id="register-password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          className="auth-field__input"
          placeholder="8文字以上のパスワード"
          {...register('password', { required: true, minLength: 8 })}
        />
        <span className="auth-field__accent" />
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
          {...register('display_name', { required: true, maxLength: 150 })}
        />
        <span className="auth-field__accent" />
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
