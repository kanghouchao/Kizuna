/**
 * 呼び名を導ける最小限。作業キュー・アーカイブの平坦な項目と、詳細の受付時の写しを受け取り、
 * どちらの行型でも（詳細でも）そのまま渡せるようにする。
 */
export interface OrderCustomerNameSource {
  customer_name?: string | null;
  contact_snapshot?: import('@/entities/order').ContactSnapshot;
  contact_name?: string;
  contact_phone_number?: string;
  requester_declared_name?: string;
}

/** 台帳の顧客に着いていない受注の呼び名に添える注記。 */
export const UNLINKED_NOTE = '（顧客未設定）';

export interface OrderCustomerLabel {
  /** 呼び名。台帳の顧客名か、それが無ければ受付で録入された連絡先（氏名、無ければ電話番号）。 */
  name: string;
  /** 呼び名が台帳の顧客名ではなく録入された連絡先であること。注記を添えるかの判定に使う。 */
  unlinked: boolean;
}

/** 台帳の名前と受付時の名乗りを区別して表示する。 */
export function customerLabel(order: OrderCustomerNameSource): OrderCustomerLabel | null {
  if (order.customer_name) {
    return { name: order.customer_name, unlinked: false };
  }
  const reported =
    order.contact_snapshot?.name ||
    order.contact_snapshot?.phone_number ||
    order.contact_name ||
    order.contact_phone_number ||
    order.requester_declared_name;
  return reported ? { name: reported, unlinked: true } : null;
}

/**
 * 取り消せない操作のモーダルが見出しに出す呼び名。相手が誰か分からないまま会計を確定したり
 * 帰属を無効化したりさせないため、顧客未設定の受注も録入された連絡先で呼ぶ。
 *
 * 見出しの行はすでに全体が注記の体裁なので、注記（{@link UNLINKED_NOTE}）だけを分けて装飾せず
 * 1 本の文字列に畳む。
 */
export function customerHeadingText(order: OrderCustomerNameSource | null): string {
  const label = order === null ? null : customerLabel(order);
  if (label === null) {
    return 'お客様名なし';
  }
  return label.unlinked ? `${label.name}${UNLINKED_NOTE}` : label.name;
}
