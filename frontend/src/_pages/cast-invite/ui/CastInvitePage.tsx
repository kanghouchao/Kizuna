'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  CastAcceptanceResponse,
  CastInvitationDetailResponse,
  castInvitationAcceptanceApi,
} from '@/entities/cast';
import { ExistingLoginForm, RegisterForm } from '@/features/cast-invite-accept';
import { AuthLayout } from '@/shared/ui';

type Stage = 'loading' | 'invalid' | 'choose' | 'register' | 'login' | 'done';

/** キャスト招待受諾ランディング（公開）。照会 → 新規登録 or 既存ログイン受諾 → 完了画面。 */
export default function CastInvitePage() {
  const [token, setToken] = useState('');
  const [stage, setStage] = useState<Stage>('loading');
  const [detail, setDetail] = useState<CastInvitationDetailResponse | null>(null);
  const [invalidMessage, setInvalidMessage] = useState('');
  const [storeName, setStoreName] = useState('');

  useEffect(() => {
    // トークンはフラグメントで届く（castInviteUrl の説明どおり、リクエストターゲットに載せない）。
    // フラグメントはサーバへ送られないため、描画後にブラウザ側で読み取るしかない。
    let raw: string;
    try {
      raw = decodeURIComponent(window.location.hash.slice(1));
    } catch {
      // 壊れたパーセント符号化（単独の % など）。復元できない以上、招待は特定できない。
      raw = '';
    }
    if (!raw) {
      setInvalidMessage('招待リンクが見つかりません。URLをご確認ください。');
      setStage('invalid');
      return;
    }
    setToken(raw);

    const load = async () => {
      try {
        const response = await castInvitationAcceptanceApi.view(raw);
        if (response.status === 'VALID') {
          setDetail(response);
          setStage('choose');
          return;
        }
        setInvalidMessage(
          response.status === 'UNAVAILABLE'
            ? 'この招待は利用できません。店舗の担当者にご確認ください。'
            : response.status === 'EXPIRED'
              ? '招待の有効期限が切れています。店舗の担当者に再発行を依頼してください。'
              : 'この招待は既に使用されています。心当たりがない場合は店舗の担当者にご確認ください。'
        );
        setStage('invalid');
      } catch {
        setInvalidMessage('招待リンクが見つかりません。URLをご確認ください。');
        setStage('invalid');
      }
    };
    void load();
    // フラグメントは入場時の一度だけ読む（token はこの効果自身の setToken 由来のため、依存に入れると再照会が回る）。
  }, []);

  const handleAccepted = (response: CastAcceptanceResponse) => {
    setStoreName(response.store_name ?? '');
    setStage('done');
  };

  if (stage === 'loading') {
    return (
      <AuthLayout title="招待を確認しています" subtitle="しばらくお待ちください">
        <p className="auth-text text-center">読み込み中...</p>
      </AuthLayout>
    );
  }

  if (stage === 'invalid') {
    return (
      <AuthLayout title="招待を利用できません" subtitle="以下の内容をご確認ください">
        <div className="auth-alert auth-alert--error">{invalidMessage}</div>
      </AuthLayout>
    );
  }

  if (stage === 'done') {
    return (
      <AuthLayout title="連携が完了しました" subtitle="ご登録ありがとうございます">
        <div className="space-y-4">
          <div className="auth-alert auth-alert--success">{storeName}との連携が完了しました</div>
          <Link href="/cast" className="auth-btn block text-center">
            ポータルへ
          </Link>
          <p className="auth-note">
            別のアカウントでログイン中の場合は、ログアウトしてから招待を受諾したアカウントでログインしてください。
          </p>
        </div>
      </AuthLayout>
    );
  }

  // detail は choose/register/login の 3 段では必ず存在する（invalid/loading/done で早期 return 済み）
  if (!detail) return null;

  if (stage === 'register') {
    return (
      <AuthLayout title="新規登録" subtitle={`${detail.store_name}のキャストとして登録します`}>
        <RegisterForm
          token={token}
          initialDisplayName={detail.cast_name ?? ''}
          onSuccess={handleAccepted}
          onBack={() => setStage('choose')}
        />
      </AuthLayout>
    );
  }

  if (stage === 'login') {
    return (
      <AuthLayout
        title="既存アカウントでログイン"
        subtitle={`${detail.store_name}の権限を追加します`}
      >
        <ExistingLoginForm
          token={token}
          onSuccess={handleAccepted}
          onBack={() => setStage('choose')}
        />
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={`${detail.store_name}からの招待`}
      subtitle="招待を受諾する方法を選択してください"
    >
      <div className="space-y-6">
        <p className="auth-text">出勤希望の提出とスケジュールの確認ができます。</p>
        <p className="auth-note">
          有効期限:{' '}
          {detail.expires_at ? new Date(detail.expires_at).toLocaleString('ja-JP') : '不明'}
        </p>
        <div className="space-y-3">
          <button type="button" onClick={() => setStage('register')} className="auth-btn">
            新規登録して受諾
          </button>
          <button
            type="button"
            onClick={() => setStage('login')}
            className="auth-btn auth-btn--secondary"
          >
            既存アカウントでログイン
          </button>
        </div>
        <p className="auth-note">
          既に他店舗のキャストとして登録済みの場合は、既存アカウントでログインすると本店舗の権限が追加されます。
        </p>
      </div>
    </AuthLayout>
  );
}
