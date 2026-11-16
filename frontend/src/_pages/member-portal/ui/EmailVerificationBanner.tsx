'use client';

import { MailIcon } from 'lucide-react';
import { memberApi } from '@/entities/member';
import { useMe } from '@/entities/user';
import { useWrite } from '@/shared/lib';
import { Button } from '@/shared/ui';

/**
 * 未検証の会員にだけ出す案内。/me が読めるまでは何も出さない（検証済みの会員に一瞬でも出さないため）。
 * 検証は機能を塞がないので、案内は画面の内容に重ねず流れの中に置く。
 * 再送を断る理由（間隔・回数の上限、既に検証済み）はサーバの文言で区別して伝える。
 */
export function EmailVerificationBanner() {
  const { me } = useMe();
  const { busy, write } = useWrite();
  if (me === null || me.user_type !== 'MEMBER' || me.email_verified) {
    return null;
  }

  const resend = () =>
    void write(() => memberApi.resendEmailVerification(), {
      success: `${me.email}に確認メールを送りました`,
      failure: '確認メールを送れませんでした',
    });

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
          disabled={busy}
          onClick={resend}
        >
          確認メールを再送する
        </Button>
      </div>
    </div>
  );
}
