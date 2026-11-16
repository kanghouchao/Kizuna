'use client';

import { useEffect, useRef } from 'react';
import { useAgeCheck } from '../../model/useAgeCheck';

const TITLE_ID = 'sfd-gate-title';

/**
 * 年齢確認の画面。modal の dialog なので、出ている間は背後のページへフォーカスも読み上げも届かない。
 * 同意の記録を読むまでと dialog が開くまでの間は、不透明な面でページを覆っておく。
 */
export default function AgeGate({ storeName }: { storeName: string }) {
  const { phase, accept, decline, leave } = useAgeCheck();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const leaveRef = useRef<HTMLButtonElement>(null);
  const asking = phase === 'gate' || phase === 'declined';

  useEffect(() => {
    const dialog = dialogRef.current;
    if (asking && dialog && !dialog.open) dialog.showModal();
  }, [asking]);

  // 拒否で押したボタンが消えるので、フォーカスを残った操作へ移す
  useEffect(() => {
    if (phase === 'declined') leaveRef.current?.focus();
  }, [phase]);

  if (phase === 'verified') return null;

  return (
    <>
      <div className="sfd-gate-cover" />
      {asking && (
        <dialog
          ref={dialogRef}
          className="sfd-gate"
          aria-labelledby={TITLE_ID}
          // Esc では閉じさせない。cancel を止めても閉じる経路が残るブラウザがあるので、閉じたら開き直す
          onCancel={e => e.preventDefault()}
          onClose={e => e.currentTarget.showModal()}
        >
          <div className="sfd-gate-inner">
            <p className="sfd-gate-store">{storeName}</p>
            {phase === 'gate' ? (
              <>
                <p className="sfd-label">Age Verification</p>
                <h2 id={TITLE_ID} className="sfd-gate-title">
                  年齢確認
                </h2>
                <p className="sfd-gate-text">
                  当サイトは18歳以上の方を対象としています。
                  <br />
                  あなたは18歳以上ですか？
                </p>
                <div className="sfd-gate-actions">
                  <button type="button" className="sfd-btn" onClick={accept}>
                    はい
                  </button>
                  <button type="button" className="sfd-btn sfd-btn-quiet" onClick={decline}>
                    いいえ
                  </button>
                </div>
                <p className="sfd-gate-note">
                  日本の法律により、18歳未満の方の閲覧を禁止しています
                </p>
              </>
            ) : (
              <>
                <p className="sfd-label">Access Restricted</p>
                <h2 id={TITLE_ID} className="sfd-gate-title">
                  アクセス制限
                </h2>
                <p className="sfd-gate-text">18歳未満の方はご利用いただけません</p>
                <div className="sfd-gate-actions">
                  <button
                    ref={leaveRef}
                    type="button"
                    className="sfd-btn sfd-btn-quiet"
                    onClick={leave}
                  >
                    ウィンドウを閉じる
                  </button>
                </div>
              </>
            )}
          </div>
        </dialog>
      )}
    </>
  );
}
