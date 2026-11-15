/** 公開サイトのナビゲーションが指すページ。並びと名前は全テンプレートで同じ。 */
export const STORE_SITE_NAV_PAGES = [
  { href: '/', label: 'TOP' },
  { href: '/casts', label: 'キャスト' },
  { href: '/schedule', label: '出勤表' },
  { href: '/news', label: 'お知らせ' },
  { href: '/reviews', label: '口コミ' },
  { href: '/menu', label: '料金' },
  { href: '/about', label: '店舗情報' },
  // 未ログインの来訪者の申請入口。会員は会員ポータルの予約入口から入る
  { href: '/reservation', label: 'ご予約' },
] as const;
