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
 * 受注の顧客の呼び名。どちらも無ければ null で、空欄の出し方は呼び出し側が決める。
 *
 * 録入された連絡先と台帳の顧客名を呼び出し側が見分けられる形で返すのは、前者が台帳に行を持たない
 * 申告のままの文字列だから。それでも出すのは、電話番号が同店で複数の顧客に一致した受注
 * （自動照合を断念して顧客未設定で成立する）は、録入した連絡先を出さないと画面のどこにも現れないため。
 */
export function customerLabel(order: Order): OrderCustomerLabel | null {
  if (order.customer_name) {
    return { name: order.customer_name, unlinked: false };
  }
  const reported = order.contact_name || order.contact_phone_number;
  return reported ? { name: reported, unlinked: true } : null;
}
