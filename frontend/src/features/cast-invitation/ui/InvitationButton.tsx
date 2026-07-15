'use client';

import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { CastInvitationStatus, castApi } from '@/entities/cast';
import { getApiErrorMessage } from '@/shared/lib';
import { InvitationModal } from './InvitationModal';

interface InvitationButtonProps {
  castId: string;
  status: CastInvitationStatus;
  /** 発行成功後（一覧の招待状態を更新するため）に呼ばれる。 */
  onIssued: () => void;
}

/** キャスト一覧の行内招待発行ボタン（未招待/期限切れ→発行、招待中→再発行、連携済みは非表示。裁定9）。 */
export function InvitationButton({ castId, status, onIssued }: InvitationButtonProps) {
  const [issuing, setIssuing] = useState(false);
  const [invitation, setInvitation] = useState<{ token: string; expiresAt: string } | null>(null);

  if (status === 'LINKED') return null;

  const handleIssue = async () => {
    setIssuing(true);
    try {
      const response = await castApi.issueInvitation(castId);
      setInvitation({ token: response.token, expiresAt: response.expires_at });
      onIssued();
    } catch (error) {
      toast.error(getApiErrorMessage(error, '招待の発行に失敗しました'));
    } finally {
      setIssuing(false);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={handleIssue}
        disabled={issuing}
        className="rounded text-blue-600 hover:text-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        {status === 'INVITED' ? '再発行' : '招待を発行'}
      </button>
      <InvitationModal
        open={invitation !== null}
        link={
          invitation && typeof window !== 'undefined'
            ? `${window.location.origin}/platform/invite/${invitation.token}`
            : ''
        }
        expiresAt={invitation?.expiresAt ?? null}
        onClose={() => setInvitation(null)}
      />
    </>
  );
}
