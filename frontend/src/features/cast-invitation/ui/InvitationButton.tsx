'use client';

import { useState } from 'react';
import { notify } from '@/shared/notify';
import { CastInvitationStatus, castApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';
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

/** キャスト一覧の行内招待発行ボタン（未招待/期限切れ→発行、招待中→再発行、連携済みは非表示。裁定9）。 */
export function InvitationButton({ castId, status, onIssued }: InvitationButtonProps) {
  const [issuing, setIssuing] = useState(false);

  if (status === 'LINKED') return null;

  const handleIssue = async () => {
    setIssuing(true);
    try {
      const response = await castApi.issueInvitation(castId);
      onIssued({ token: response.token ?? '', expiresAt: response.expires_at ?? '' });
    } catch (error) {
      notify.error(getApiErrorMessage(error, '招待の発行に失敗しました'));
    } finally {
      setIssuing(false);
    }
  };

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      onClick={handleIssue}
      disabled={issuing}
      className="text-primary-strong"
    >
      {status === 'INVITED' ? '再発行' : '招待を発行'}
    </Button>
  );
}
