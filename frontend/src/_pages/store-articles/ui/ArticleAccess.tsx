import type { ReactNode } from 'react';
import { MePending, hasPermission, useMe } from '@/entities/user';

export function ArticleAccess({ children }: { children: ReactNode }) {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (me.user_type !== 'STAFF' || !hasPermission(me, 'ARTICLE_MANAGE'))
    return <div role="alert">記事の管理権限がありません</div>;
  return <>{children}</>;
}
