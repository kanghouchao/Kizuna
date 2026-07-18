'use client';

import Cookies from 'js-cookie';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { CastAcceptanceResponse, castInvitationAcceptanceApi } from '@/entities/cast';
import { PlatformLoginRequest, platformAuthApi } from '@/entities/user';
import {
  clearPlatformSession,
  getApiErrorMessage,
  getPlatformConsole,
  getPlatformStoreId,
  setPlatformStore,
  startPlatformSession,
} from '@/shared/lib';

interface ExistingLoginFormProps {
  token: string;
  onSuccess: (response: CastAcceptanceResponse) => void;
  onBack: () => void;
}

/**
 * 既存アカウント（CAST ロール限定）での招待受諾。
 * インラインでログインしてから acceptance/existing を呼ぶ（自動ログイン状態のまま完了画面へは遷移しない）。
 */
export function ExistingLoginForm({ token, onSuccess, onBack }: ExistingLoginFormProps) {
  const {
    register,
    handleSubmit,
    formState: { isSubmitting },
  } = useForm<PlatformLoginRequest>({ defaultValues: { email: '', password: '' } });

  const submit = async (values: PlatformLoginRequest) => {
    let newTokenWritten = false;
    // ログイン成功で旧セッションを消す前に退避しておく。受諾API失敗時にこれが無いと、
    // clearPlatformSession() 済みの旧 platform-role(コンソール値)/platform-store-id に戻れず訪問者がログアウト状態に落ちる（#327 codex指摘）
    const previousConsole = getPlatformConsole();
    const previousStoreId = getPlatformStoreId();
    const previousToken = Cookies.get('token');
    try {
      // グローバル401ハンドラ（apiClient）に token 除去+リダイレクトをやらせない。ここでの401（パスワード誤り/
      // アカウント無効化）は下の catch 節で自前のセッション退避・復元を行うため、先に画面遷移されると無意味になる
      // （#327 codex指摘）
      const { token: authToken, expires_at } = await platformAuthApi.login(values, {
        skipAuthRedirect: true,
      });
      // 招待を開く前に別ロールで平台にログイン済みだった場合、旧セッションの platform-role/platform-store-id が
      // 残ったままだと apiClient やルート遷移が旧ロールの文脈を CAST の token に対して使ってしまう（#327 codex指摘）
      clearPlatformSession();
      Cookies.set('token', authToken, { expires: new Date(expires_at) });
      newTokenWritten = true;
      const response = await castInvitationAcceptanceApi.acceptAsExistingUser(token);
      // 受諾はここで完了し、ポータルセッションは開始しない（#328 未着手）。一時的に張った CAST token を
      // 残すと token 単独の存在チェックで central/tenant コンソールへ誤って通されるため必ず消去する（#327 codex指摘）
      Cookies.remove('token');
      onSuccess(response);
    } catch (error) {
      // login 自体が失敗した場合は新 token を書き込んでいないため、既存セッションの token には触れない
      // （書き込み後の失敗のみ後始末する。#327 codex指摘）
      if (newTokenWritten) {
        Cookies.remove('token');
        // 受諾APIの失敗（招待の失効/競合、通信断など）。旧セッションが存在した場合はここで復元する。
        // 未ログイン訪問者だった場合（退避値が無い）は何もしない（#327 codex指摘）
        if (previousConsole) {
          startPlatformSession(previousConsole);
        }
        if (previousStoreId) {
          setPlatformStore(previousStoreId);
        }
        if (previousToken) {
          Cookies.set('token', previousToken);
        }
      }
      toast.error(getApiErrorMessage(error, 'ログインまたは受諾に失敗しました'));
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-7">
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
          {...register('email', { required: true })}
        />
        <span className="auth-field__accent" />
      </div>

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
          {...register('password', { required: true })}
        />
        <span className="auth-field__accent" />
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
