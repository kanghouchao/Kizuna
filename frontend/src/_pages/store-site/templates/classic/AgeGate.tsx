'use client';

import { AlertDialog } from '@base-ui/react/alert-dialog';
import { useRef } from 'react';
import { useAgeCheck } from '../../model/useAgeCheck';
import { classicRootClass } from './fonts';

interface AgeGateProps {
  storeName: string;
}

/**
 * 年齢確認の画面。同意するまで後ろのページへフォーカスが抜けないよう、モーダルとして開く。
 * 覆いは同意の記録を読む前から敷き、ページが一瞬でも先に見えないようにする。
 * 開いた直後のフォーカスは「いいえ」に置く。「はい」に置くと Enter 一つで同意が記録される。
 */
export default function AgeGate({ storeName }: AgeGateProps) {
  const { phase, accept, decline, leave } = useAgeCheck();
  const declined = phase === 'declined';
  const declineRef = useRef<HTMLButtonElement>(null);

  return (
    <>
      {phase !== 'verified' && <div className="c-gate-cover" />}
      <AlertDialog.Root open={phase === 'gate' || declined}>
        <AlertDialog.Portal className={classicRootClass}>
          <AlertDialog.Backdrop className="c-gate-backdrop" />
          <AlertDialog.Popup className="c-gate" initialFocus={declineRef}>
            <div className="c-gate-card">
              <p className="c-gate-name c-v c-name c-display">{storeName}</p>
              {declined ? (
                <div className="c-gate-body">
                  <AlertDialog.Title className="c-gate-title c-display">
                    アクセス制限
                  </AlertDialog.Title>
                  <AlertDialog.Description className="c-gate-text">
                    18歳未満の方はご利用いただけません
                  </AlertDialog.Description>
                  <div className="c-gate-actions">
                    <button type="button" className="c-gate-no" onClick={leave} autoFocus>
                      ウィンドウを閉じる
                    </button>
                  </div>
                </div>
              ) : (
                <div className="c-gate-body">
                  <AlertDialog.Title className="c-gate-title c-display">年齢確認</AlertDialog.Title>
                  <AlertDialog.Description className="c-gate-text">
                    このサイトは18歳以上の方のみ閲覧可能です
                  </AlertDialog.Description>
                  <p className="c-gate-ask">あなたは18歳以上ですか？</p>
                  <div className="c-gate-actions">
                    <button type="button" className="c-gate-yes" onClick={accept}>
                      はい
                    </button>
                    <button ref={declineRef} type="button" className="c-gate-no" onClick={decline}>
                      いいえ
                    </button>
                  </div>
                  <p className="c-gate-note">
                    日本の法律により、18歳未満の方の閲覧を禁止しています
                  </p>
                </div>
              )}
            </div>
          </AlertDialog.Popup>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </>
  );
}
