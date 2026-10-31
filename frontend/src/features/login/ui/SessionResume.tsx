'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { authApi } from '@/entities/user';
import { clearSessionToken, requestRefresh } from '@/shared/api';
import { hasMemberReturnPath } from '@/shared/lib';
import { landSession } from '../model/completeLogin';

/**
 * ログイン画面を開いたときにリフレッシュを 1 回試し、ログイン済みならフォームを出さずに着地先へ移す。
 * ログアウトせずに別アカウントでログインし、タブ間で本人が食い違う状態に入る主な入口を塞ぐ。
 * どの失敗でもフォームを出す（ここでは差し戻しも再ログインの表示もしない）。
 */
export function SessionResume({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      const result = await requestRefresh();
      // StrictMode の二度目の effect と競わせない。着地は会員の戻り先を 1 度だけ取り出すため、二重に走らせない。
      if (!active) return;
      if (result.status === 'ok') {
        try {
          const me = await authApi.me();
          // 会員ポータルが会員以外のセッションを 403 で拒んで戻り先を預けた場合は、着地させずに会員で入り直させる。
          // 着地すると戻り先を消費し、会員でログインし直しても伝票の画面へ戻れない。
          const landing =
            me.user_type !== 'MEMBER' && hasMemberReturnPath()
              ? null
              : landSession(me, result.refreshExpiresAt);
          if (landing?.status === 'ok') {
            router.replace(landing.path);
            return;
          }
        } catch {
          // 着地先を決められなければフォームから入り直させる
        }
        clearSessionToken();
      }
      if (active) setShowForm(true);
    })();
    return () => {
      active = false;
    };
  }, [router]);

  return showForm ? children : null;
}
