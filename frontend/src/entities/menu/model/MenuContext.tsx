'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { menuApi } from '../api/menu';
import type { MenuTree } from './types';

/** null = 読み込み中。取得に失敗したら木の無い利用者と同じ [] にする（全員の着地先への導線だけが残る）。 */
const MenuContext = createContext<MenuTree[] | null | undefined>(undefined);

/** 本人のメニューの木（GET /menus/me）。管理画面シェルに1つだけ搭載し、サイドバーと着地の判定が共有する。 */
export function MenuProvider({ children }: { children: React.ReactNode }) {
  const [trees, setTrees] = useState<MenuTree[] | null>(null);

  useEffect(() => {
    let active = true;
    menuApi.getMenus().then(
      found => {
        if (active) setTrees(found);
      },
      (reason: unknown) => {
        console.error('Failed to fetch menus', reason);
        if (active) setTrees([]);
      }
    );
    return () => {
      active = false;
    };
  }, []);

  return <MenuContext.Provider value={trees}>{children}</MenuContext.Provider>;
}

export function useMenuTrees(): MenuTree[] | null {
  const trees = useContext(MenuContext);
  if (trees === undefined) {
    throw new Error('useMenuTrees must be used within a MenuProvider');
  }
  return trees;
}
