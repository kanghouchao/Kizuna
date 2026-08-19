/** 領域が自分の失敗を自分で名乗るための組（DESIGN.md 条項①）。 */

/**
 * 取得に失敗しているかと、読み直す口。この 2 つは必ず対で要る — 片方だけ渡せる形にすると、
 * 出口の無い失敗態か、押しても何も起きない再試行のどちらかが書けてしまう。
 */
export interface RegionFailure {
  failed: boolean;
  onRetry: () => void;
}
