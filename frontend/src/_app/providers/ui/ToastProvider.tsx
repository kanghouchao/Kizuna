'use client';

import { toastManager } from '@/shared/notify';
import { Toaster } from '@/shared/ui';

/**
 * 通知の描画口。語義層が持つ実体を渡して繋ぐ。段ごとの姿は `shared/ui/toast.tsx` が
 * `data-type` から引くので、ここには規範値が一つも無い。
 */
export function ToastProvider() {
  return <Toaster toastManager={toastManager} />;
}
