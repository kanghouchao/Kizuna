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
          style: { background: '#16a34a' }, // green-600
        },
        error: {
          duration: 5000,
          style: { background: '#dc2626' }, // red-600
        },
      }}
    />
  );
}
