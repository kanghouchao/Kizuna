'use client';

import { CastInvitationStatus, castApi } from '@/entities/cast';
import { useWrite } from '@/shared/lib';
import { Button } from '@/shared/ui';

export interface IssuedInvitation {
  token: string;
  expiresAt: string;
}

interface InvitationButtonProps {
  castId: string | undefined;
  status: CastInvitationStatus | undefined;
  /**
   * 発行成功後に呼ばれる。モーダル表示は呼び出し元（ページ層）の責務 ——
   * 一覧の再取得（onIssued 内で行われる想定）は isLoading を伴い、それに連動して
   * このボタン自身（テーブル行）がアンマウントされ得るため、ここではモーダル state を持たない。
   */
  onIssued: (invitation: IssuedInvitation) => void;
}

/** キャスト一覧の行内招待発行ボタン。連携済み・招待不可の在籍では表示しない。 */
export function InvitationButton({ castId, status, onIssued }: InvitationButtonProps) {
  const { busy, write } = useWrite();

  if (status === 'LINKED' || status === 'UNAVAILABLE') return null;

  const handleIssue = () =>
    write(() => castApi.issueInvitation(castId), {
      failure: '招待の発行に失敗しました',
      onSuccess: response =>
        onIssued({ token: response.token ?? '', expiresAt: response.expires_at ?? '' }),
    });

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      onClick={handleIssue}
      disabled={busy}
      className="text-primary-strong"
    >
      {status === 'INVITED' ? '再発行' : '招待を発行'}
    </Button>
  );
}
