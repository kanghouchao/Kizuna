'use client';

import { useEffect, useState } from 'react';
import { platformAuthApi, useAuth } from '@/entities/user';

/** アカウントタブ。表示名とログアウトのみの最小画面。 */
export function CastAccountPage() {
  const { logout } = useAuth();
  const [displayName, setDisplayName] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    platformAuthApi
      .me()
      .then(me => {
        if (!cancelled) setDisplayName(me.display_name);
      })
      .catch(() => {
        // シェルが mount 時に本人確認済みのため通常は到達しない。表示名は未取得のまま留める。
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="p-4">
      <div className="rounded-[10px] border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-xs font-medium uppercase tracking-wider text-gray-500">表示名</p>
        <p className="mt-1 text-lg font-semibold text-gray-900">{displayName ?? '読み込み中...'}</p>
        <button
          type="button"
          onClick={logout}
          className="mt-6 w-full rounded-[10px] border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        >
          ログアウト
        </button>
      </div>
    </div>
  );
}
