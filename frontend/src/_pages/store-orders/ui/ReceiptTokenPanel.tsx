'use client';

import { QRCodeSVG } from 'qrcode.react';
import { receiptClaimUrl } from '../lib/receiptClaimUrl';
import { useAppDomain } from '@/shared/lib';

interface ReceiptTokenPanelProps {
  /** 発行された伝票トークンの生値。保存されるのはダイジェストだけなので、この値は応答にしか現れない。 */
  token: string;
  /** 誰がいつまでに読み取れるかの一文。発行の文脈（完了時 / 訂正の再発行）で違うので呼出側が持つ。 */
  claimNote: string;
}

/**
 * 発行された伝票 QR を出す一画面。完了時の発行と訂正の再発行が同じ表示を共有する。
 *
 * 生値はこの表示にしか現れず、閉じた後に出し直す経路が無い。だから「今だけ表示できる」ことを
 * 先に伝え、閉じる手段を明示のボタンだけに絞る — 呼出側は閉じるボタンを操作帯に置き、QR を
 * 出している間は ESC・背景押下・右上の × で閉じないようにしておくこと（この区画だけでは塞げない）。
 */
export function ReceiptTokenPanel({ token, claimNote }: ReceiptTokenPanelProps) {
  const appDomain = useAppDomain();
  return (
    <div className="flex flex-col items-center gap-4">
      <div className="rounded-xl border bg-card p-4">
        <QRCodeSVG
          value={receiptClaimUrl(token, appDomain)}
          size={192}
          aria-label="伝票QR"
          role="img"
        />
      </div>
      {/* 生値はこの応答にしか現れない。閉じた後に出し直す経路が無いことを先に伝える */}
      <p className="text-sm text-foreground">
        この QR は今だけ表示できます。閉じると再表示できません。
      </p>
      <p className="text-sm text-muted-foreground">{claimNote}</p>
    </div>
  );
}
