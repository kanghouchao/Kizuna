'use client';

import { useState } from 'react';
import { MailIcon } from 'lucide-react';
import { memberApi } from '@/entities/member';
import { useMe } from '@/entities/user';
import { getApiErrorMessage } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Button } from '@/shared/ui';

/**
 * 未検証の会員にだけ出す案内。/me が読めるまでは何も出さない（検証済みの会員に一瞬でも出さないため）。
 * 検証は機能を塞がないので、案内は画面の内容に重ねず流れの中に置く。
 * 再送を断る理由（間隔・回数の上限、既に検証済み）はサーバの文言で区別して伝える。
 */
export function EmailVerificationBanner() {
  const { me } = useMe();
  const [sending, setSending] = useState(false);
  if (me === null || me.user_type !== 'MEMBER' || me.email_verified) {
    return null;
  }

  const resend = async () => {
    setSending(true);
    try {
      await memberApi.resendEmailVerification();
      notify.success(`${me.email}に確認メールを送りました`);
    } catch (error) {
      notify.error(getApiErrorMessage(error, '確認メールを送れませんでした'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="px-4 pt-4">
      <div role="status" className="rounded-lg bg-warning/10 p-3 text-sm text-warning-strong">
        <div className="flex gap-2">
          <MailIcon className="size-5 shrink-0" aria-hidden="true" />
          <p>
            メールアドレスの確認が済んでいません。
            <span className="break-all">{me.email}</span>
            に確認メールを送りました。メール内のリンクを開いて確認を完了してください。
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-2 ml-7"
          disabled={sending}
          onClick={resend}
        >
          確認メールを再送する
        </Button>
      </div>
    </div>
  );
}
