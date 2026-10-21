'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import Cookies from 'js-cookie';
import { notify } from '@/shared/notify';
import { LineAuthorizationRequest, platformAuthApi, platformLineApi } from '@/entities/user';
import { completePlatformLogin } from '@/features/platform-login';
import {
  consumeLineAuthorization,
  getApiErrorMessage,
  lineCallbackRedirectUri,
  startPlatformSession,
  takeMemberReturnPath,
} from '@/shared/lib';
import { AuthLayout } from '@/shared/ui';
import LineRegisterStep, { LineRegisterValues } from './LineRegisterStep';

type Stage = 'processing' | 'register' | 'error';

/** エラー表示の戻り先。連携（ログイン済み）はアカウント設定、それ以外はログイン画面。 */
type BackTo = 'login' | 'settings';

const BACK_LINKS: Record<BackTo, { href: string; label: string }> = {
  login: { href: '/platform/login', label: 'ログイン画面へ戻る' },
  settings: { href: '/platform/settings/account', label: 'アカウント設定へ戻る' },
};

/**
 * LINE 認可のコールバック（公開）。ログイン・会員登録・既存アカウントへの連携の
 * 三つの入口が同じ URL に戻るため、開始時に保存した意図で分岐する。
 */
export default function LineCallbackPage() {
  const router = useRouter();
  const startedRef = useRef(false);
  const [stage, setStage] = useState<Stage>('processing');
  const [errorMessage, setErrorMessage] = useState('');
  const [backTo, setBackTo] = useState<BackTo>('login');
  const [registrationTicket, setRegistrationTicket] = useState('');
  const [lineDisplayName, setLineDisplayName] = useState('');

  useEffect(() => {
    // 保存した state/code_verifier は一度きりの消費のため、開発時の二重実行で空振りさせない
    if (startedRef.current) return;
    startedRef.current = true;

    const fail = (message: string, destination: BackTo) => {
      setErrorMessage(message);
      setBackTo(destination);
      setStage('error');
    };

    const runLogin = async (request: LineAuthorizationRequest) => {
      const response = await platformLineApi.login(request);
      if (!response.registered) {
        setRegistrationTicket(response.registration_ticket ?? '');
        setLineDisplayName(response.display_name ?? '');
        setStage('register');
        return;
      }
      const completion = await completePlatformLogin(response);
      if (completion.status === 'unsupported') {
        fail('この利用者種別のポータルは準備中です', 'login');
        return;
      }
      // 消費済みのコールバック URL を履歴に残さない（戻るで再実行するとエラー画面になるだけのため）
      router.replace(completion.path);
    };

    const runLink = async (request: LineAuthorizationRequest, subject: string | undefined) => {
      // 発起主体と現在のログインが一致するときだけ連携する。外部認可の往復中に別アカウントへ
      // 切り替わっていた場合、そのまま送ると意図しないアカウントに LINE が恒久的に付く（解除は未提供）。
      const me = await platformAuthApi.me();
      if (!subject || me.email !== subject) {
        fail(
          'LINE連携を開始したアカウントと現在のログインが一致しません。アカウント設定からやり直してください',
          'settings'
        );
        return;
      }
      try {
        await platformLineApi.link(request);
      } catch (error) {
        // 409 には「本人が連携済み」「他の身分が連携済み」の二つの原因があり、区別はサーバーの文言が正
        fail(getApiErrorMessage(error, 'LINE連携に失敗しました'), 'settings');
        return;
      }
      router.replace('/platform/settings/account');
    };

    const run = async () => {
      const params = new URLSearchParams(window.location.search);
      const authorization = consumeLineAuthorization(params.get('state'));
      const destination: BackTo = authorization?.intent === 'link' ? 'settings' : 'login';

      // 利用者が LINE 側で拒否した場合など。error_description は英語のため表示しない
      if (params.get('error')) {
        fail('LINEでの認証が完了しませんでした', destination);
        return;
      }

      const code = params.get('code');
      if (!code || !authorization) {
        fail('認証を確認できませんでした。お手数ですが最初からやり直してください', destination);
        return;
      }

      const request: LineAuthorizationRequest = {
        code,
        redirect_uri: lineCallbackRedirectUri(),
        code_verifier: authorization.verifier,
      };

      try {
        if (authorization.intent === 'link') {
          await runLink(request, authorization.subject);
          return;
        }
        await runLogin(request);
      } catch (error) {
        fail(getApiErrorMessage(error, 'LINEでの認証に失敗しました'), destination);
      }
    };

    void run();
  }, [router]);

  const submitRegistration = async (values: LineRegisterValues) => {
    try {
      const { token, expires_at } = await platformLineApi.register({
        registration_ticket: registrationTicket,
        display_name: values.display_name,
        email: values.email,
      });
      // epoch millis を Date に変換する（expires_at をそのまま日数として解釈すると不正な有効期限になる）
      Cookies.set('token', token ?? '', { expires: new Date(expires_at) });
      startPlatformSession('member', expires_at);
      // 会員ポータルで弾かれた人が LINE 登録へ回ってくる経路がある（伝票の QR を未登録で開いた等）。
      // 預かった戻り先は会員セッションが立った時点で必ず消費する — 残すと、同じタブで後からログインした
      // 別の会員がその戻り先へ運ばれる。
      // 消費済みのコールバック URL と使用済みフォームを履歴に残さない
      router.replace(takeMemberReturnPath() ?? '/member/');
    } catch (error) {
      notify.error(getApiErrorMessage(error, '会員登録に失敗しました'));
    }
  };

  if (stage === 'error') {
    const back = BACK_LINKS[backTo];
    return (
      <AuthLayout title="LINE認証を完了できません" subtitle="以下の内容をご確認ください">
        <div className="space-y-6">
          <div className="auth-alert auth-alert--error">{errorMessage}</div>
          <p className="text-center text-xs text-[#9a958e]">
            <Link href={back.href} className="auth-link">
              {back.label}
            </Link>
          </p>
        </div>
      </AuthLayout>
    );
  }

  if (stage === 'register') {
    return (
      <AuthLayout title="会員登録" subtitle="LINEアカウントで登録します。内容をご確認ください">
        <LineRegisterStep defaultDisplayName={lineDisplayName} onSubmit={submitRegistration} />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="LINE認証を確認しています" subtitle="しばらくお待ちください">
      <p className="text-center text-sm text-[#9a958e]">読み込み中...</p>
    </AuthLayout>
  );
}
