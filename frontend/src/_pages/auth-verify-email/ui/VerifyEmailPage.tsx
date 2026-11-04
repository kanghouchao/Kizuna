'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { isAxiosError } from 'axios';
import { emailVerificationApi } from '@/entities/user';
import { AuthLayout } from '@/shared/ui';

type Result =
  { status: 'loading' } | { status: 'verified' } | { status: 'failed'; message: string };

const REJECTED =
  '確認リンクが無効か、有効期限が切れています。新しい確認メールのリンクを開いてください。';

/** 検証メールのリンクの着地（公開）。フラグメントのトークンを本文で送り、メールアドレスを確認する。 */
export default function VerifyEmailPage() {
  const [result, setResult] = useState<Result>({ status: 'loading' });
  // トークンは一度しか使えない。開発時の Strict Mode が入場の効果を二度走らせても送信は一度に限る —
  // 二度目は消費済みで拒否され、先の成功を失敗で上書きしてしまう。
  const sent = useRef(false);

  useEffect(() => {
    if (sent.current) return;
    sent.current = true;
    // トークンはフラグメントで届く（リクエストターゲットに載せない）。描画後にブラウザ側で読むしかない。
    let token: string;
    try {
      token = decodeURIComponent(window.location.hash.slice(1));
    } catch {
      token = '';
    }
    if (!token) {
      setResult({ status: 'failed', message: REJECTED });
      return;
    }
    emailVerificationApi
      .confirm(token)
      .then(() => setResult({ status: 'verified' }))
      .catch((error: unknown) =>
        setResult({
          status: 'failed',
          message:
            isAxiosError(error) && error.response?.status === 400
              ? REJECTED
              : '確認を完了できませんでした。時間をおいて、メールのリンクを開き直してください。',
        })
      );
  }, []);

  if (result.status === 'loading') {
    return (
      <AuthLayout title="メールアドレスを確認しています" subtitle="しばらくお待ちください">
        <p className="text-center text-sm text-[#9a958e]">確認中...</p>
      </AuthLayout>
    );
  }

  if (result.status === 'failed') {
    return (
      <AuthLayout
        title="メールアドレスを確認できませんでした"
        subtitle="以下の内容をご確認ください"
      >
        <div className="auth-alert auth-alert--error" role="alert">
          {result.message}
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="メールアドレスを確認しました" subtitle="ご協力ありがとうございます">
      <div className="space-y-6">
        <div className="auth-alert auth-alert--success" role="status">
          メールアドレスの確認が完了しました。
        </div>
        <Link href="/auth/login" className="auth-btn block text-center">
          ログイン画面へ
        </Link>
      </div>
    </AuthLayout>
  );
}
