import { formatMoney, formatMoneyChange } from '@/shared/lib/portal-format';
import { cn } from '@/shared/lib/utils';

interface MoneyProps {
  amount: number;
  /** 渡すと「¥8,000 → ¥10,000」の変更として出す。 */
  before?: number;
  className?: string;
}

/** 金額の表示。書式は formatMoney、桁は tabular-nums で揃える。 */
export function Money({ amount, before, className }: MoneyProps) {
  return (
    <span className={cn('tabular-nums', className)}>
      {before === undefined ? formatMoney(amount) : formatMoneyChange(before, amount)}
    </span>
  );
}
