'use client';

import { Toaster } from 'react-hot-toast';

/**
 * Global toast provider for consistent top-center notifications.
 * Keep visual styles and durations centralized here.
 */
export function ToastProvider() {
  return (
    <Toaster
      position="top-center"
      toastOptions={{
        duration: 4000,
        style: {
          background: '#1f2937', // gray-800
          color: '#fff',
          borderRadius: '10px',
        },
        success: {
          duration: 2500,
          // react-hot-toast はインライン style しか受けないため、success トークンは
          // CSS 変数で参照する（bg-success + text-success-foreground、6.18/11.20 — 両モード AA 達成）。
          style: { background: 'var(--success)', color: 'var(--success-foreground)' },
        },
        error: {
          duration: 5000,
          style: { background: '#dc2626' }, // red-600
        },
      }}
    />
  );
}
