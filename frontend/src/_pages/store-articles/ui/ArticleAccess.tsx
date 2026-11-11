import type { ReactNode } from 'react';
import { MePending, hasPermission, useMe } from '@/entities/user';
import { DeniedPage } from '@/widgets/admin-shell';

export function ArticleAccess({ children }: { children: ReactNode }) {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (me.user_type !== 'STAFF' || !hasPermission(me, 'ARTICLE_MANAGE')) return <DeniedPage />;
  return <>{children}</>;
}
