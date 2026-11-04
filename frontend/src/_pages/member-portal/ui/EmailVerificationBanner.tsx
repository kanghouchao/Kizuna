'use client';

import { MailIcon } from 'lucide-react';
import { useMe } from '@/entities/user';

/**
 * 未検証の会員にだけ出す案内。/me が読めるまでは何も出さない（検証済みの会員に一瞬でも出さないため）。
 * 検証は機能を塞がないので、案内は画面の内容に重ねず流れの中に置く。
 */
export function EmailVerificationBanner() {
  const { me } = useMe();
  if (me === null || me.user_type !== 'MEMBER' || me.email_verified) {
    return null;
  }
  return (
    <div className="mx-auto w-full max-w-md px-4 pt-4">
      <div
        role="status"
        className="flex gap-2 rounded-lg bg-warning/10 p-3 text-sm text-warning-strong"
      >
        <MailIcon className="size-5 shrink-0" aria-hidden="true" />
        <p>
          メールアドレスの確認が済んでいません。
          <span className="break-all">{me.email}</span>
          に確認メールを送りました。メール内のリンクを開いて確認を完了してください。
        </p>
      </div>
    </div>
  );
}
