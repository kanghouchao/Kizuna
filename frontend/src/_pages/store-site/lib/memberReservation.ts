/**
 * 会員ポータルの予約入口の URL。ポータルはアプリドメイン側にあり、訪問中の店舗ドメインを公開照会に
 * 突き合わせて店舗を解決するので、店舗 ID ではなくドメインを渡す。
 */
export const memberReservationUrl = (appOrigin: string, storeHostname: string) =>
  `${appOrigin}/member/reservations/new?store=${encodeURIComponent(storeHostname)}`;
