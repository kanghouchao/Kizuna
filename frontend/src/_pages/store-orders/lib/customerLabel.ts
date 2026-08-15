import { Order } from '@/entities/order';

/** 台帳の顧客に着いていない受注の呼び名に添える注記。 */
export const UNLINKED_NOTE = '（顧客未設定）';

export interface OrderCustomerLabel {
  /** 呼び名。台帳の顧客名か、それが無ければ受付で録入された連絡先（氏名、無ければ電話番号）。 */
  name: string;
  /** 呼び名が台帳の顧客名ではなく録入された連絡先であること。注記を添えるかの判定に使う。 */
  unlinked: boolean;
}

/**
 * 受注の顧客の呼び名。どれも無ければ null で、空欄の出し方は呼び出し側が決める。
 *
 * 録入された連絡先・申請時の名乗りと台帳の顧客名を呼び出し側が見分けられる形で返すのは、前者が台帳に
 * 行を持たない申告のままの文字列だから。それでも出すのは、電話番号が同店で複数の顧客に一致した受注
 * （自動照合を断念して顧客未設定で成立する）と、当店に台帳行の無い会員の未確定申請（確定するまで顧客が
 * 着かない）が、申告のままの名乗りを出さないと画面のどこにも現れないため。
 *
 * 並びは呼び名の正本の近い順。サーバ側の検索（{@code OrderSearchQuery}）も同じ 3 つを同じ順で見る。
 */
export function customerLabel(order: Order): OrderCustomerLabel | null {
  if (order.customer_name) {
    return { name: order.customer_name, unlinked: false };
  }
  const reported =
    order.contact_name || order.contact_phone_number || order.requester_declared_name;
  return reported ? { name: reported, unlinked: true } : null;
}

/**
 * 取り消せない操作のモーダルが見出しに出す呼び名。相手が誰か分からないまま会計を確定したり
 * 帰属を無効化したりさせないため、顧客未設定の受注も録入された連絡先で呼ぶ。
 *
 * 見出しの行はすでに全体が注記の体裁なので、注記（{@link UNLINKED_NOTE}）だけを分けて装飾せず
 * 1 本の文字列に畳む。
 */
export function customerHeadingText(order: Order | null): string {
  const label = order === null ? null : customerLabel(order);
  if (label === null) {
    return 'お客様名なし';
  }
  return label.unlinked ? `${label.name}${UNLINKED_NOTE}` : label.name;
}
