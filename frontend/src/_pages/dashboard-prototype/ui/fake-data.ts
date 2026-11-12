// 試作専用の固定データ。バックエンドには触れない。

export const TODAY_LABEL = '営業日 11/12（木）・14:05 時点';
export const MONTH_LABEL = '11/1〜11/12（木）';
export const PREV_MONTH_LABEL = '前月同時点（10/1〜10/12）';

export const storeOrders = { reserved: 14, serving: 3, completed: 9, cancelled: 2 };
export const storeTodaySales = { amount: 186_000, count: 9 };

const DAILY = [
  212_000, 168_000, 254_000, 231_000, 199_000, 276_000, 305_000, 188_000, 221_000, 247_000, 263_000,
  186_000,
];
export const storeDaily = DAILY.map((sales, i) => ({ day: `${i + 1}`, sales }));
export const storeMonthSales = DAILY.reduce((a, b) => a + b, 0);
export const storePrevMonthSales = 2_480_000;

export const storeTodos = { orderApplications: 5, shiftRequests: 2, reviews: 7 };

const SHORT_NAMES = [
  '新宿',
  '渋谷',
  '池袋',
  '上野',
  '品川',
  '横浜',
  '大宮',
  '千葉',
  '川崎',
  '町田',
];
const LONG_NAME = 'ラグジュアリースパ＆リラクゼーション 新宿西口駅前本店 プレミアムラウンジ館';

export type FakeStore = {
  id: number;
  name: string;
  status: 'ACTIVE' | 'PREPARING';
  monthSales: number;
  monthCount: number;
};

export function fakeStores(count: number, longNames: boolean): FakeStore[] {
  return Array.from({ length: count }, (_, i) => {
    const base = SHORT_NAMES[i % SHORT_NAMES.length];
    const suffix = i >= SHORT_NAMES.length ? `${Math.floor(i / SHORT_NAMES.length) + 1}号店` : '店';
    const name = longNames && i % 3 === 0 ? `${LONG_NAME}${i + 1}` : `${base}${suffix}`;
    const preparing = i % 7 === 6;
    const monthSales = preparing ? 0 : Math.round((3_400_000 - i * 61_000) / 1000) * 1000;
    return {
      id: 1000 + i,
      name,
      status: (preparing ? 'PREPARING' : 'ACTIVE') as FakeStore['status'],
      monthSales,
      monthCount: preparing ? 0 : Math.round(monthSales / 21_000),
    };
  }).sort((a, b) => b.monthSales - a.monthSales);
}

export function groupTotals(stores: FakeStore[]) {
  const monthSales = stores.reduce((a, s) => a + s.monthSales, 0);
  const monthCount = stores.reduce((a, s) => a + s.monthCount, 0);
  return {
    todaySales: Math.round(monthSales / 12.6 / 1000) * 1000,
    todayCount: Math.round(monthCount / 12.6),
    monthSales,
    monthCount,
    active: stores.filter(s => s.status === 'ACTIVE').length,
    preparing: stores.filter(s => s.status === 'PREPARING').length,
  };
}

// ---- 第一弾に無い候補区画の偽データ（既存の端点で取れるものだけ）

export const storeMonthRemuneration = 1_320_000;
export const storeMonthAdvertising = 180_000;
export const storeAttendance = { scheduled: 8, clockedIn: 5, absent: 1 };
export const storeApplicantsInterview = 3;
export const storeRecentNotifications = [
  { title: '11月の出勤キャンペーンのお知らせ', status: '送信済', at: '11/12（木）10:00' },
  { title: 'ご予約の確認', status: '失敗', at: '11/12（木）9:41' },
  { title: '口コミ投稿のお礼', status: '送信済', at: '11/11（水）21:15' },
];

export const groupMonthRemuneration = 37_200_000;
export const groupMonthAdvertising = 4_860_000;
export const groupMembers = 12_480;
export const groupRecentTasks = [
  { title: 'ポイントの失効', status: '成功', at: '11/12（木）3:00' },
  { title: '会員ランクの再計算', status: '失敗', at: '11/12（木）4:00' },
  { title: '予約リマインドの送信', status: '成功', at: '11/12（木）9:00' },
];
export const groupRecentMemberNotifications = [
  { title: '11月のポイント2倍キャンペーン', status: '送信済', at: '11/10（火）12:00' },
  { title: 'ポイント失効のお知らせ', status: '送信済', at: '11/1（日）9:00' },
  { title: '誕生日クーポン', status: '失敗', at: '11/1（日）9:00' },
];
export const groupRecentAudit = [
  { title: '従業員「山田」のロールを変更', status: '統括 佐藤', at: '11/12（木）13:52' },
  { title: '店舗「渋谷店」を稼働中に変更', status: '統括 佐藤', at: '11/12（木）11:20' },
  { title: 'ロール「経理」を作成', status: '統括 鈴木', at: '11/11（水）18:03' },
];
