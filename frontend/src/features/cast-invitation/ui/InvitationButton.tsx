'use client';

import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastInvitationStatus, castApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';

export interface IssuedInvitation {
  token: string;
  expiresAt: string;
}

interface InvitationButtonProps {
  castId: string;
  status: CastInvitationStatus;
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
      onIssued({ token: response.token, expiresAt: response.expires_at });
    } catch (error) {
      toast.error(getApiErrorMessage(error, '招待の発行に失敗しました'));
    } finally {
      setIssuing(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleIssue}
      disabled={issuing}
      className="rounded text-blue-600 hover:text-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
    >
      {status === 'INVITED' ? '再発行' : '招待を発行'}
    </button>
  );
}
