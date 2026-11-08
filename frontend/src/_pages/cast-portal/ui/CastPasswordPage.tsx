'use client';

import { PasswordChangeForm } from '@/features/password-change';

/** パスワード変更（アカウントタブの下位画面）。戻る導線は外枠の題名帯が出す。 */
export function CastPasswordPage() {
  return <PasswordChangeForm layout="portal" />;
}
