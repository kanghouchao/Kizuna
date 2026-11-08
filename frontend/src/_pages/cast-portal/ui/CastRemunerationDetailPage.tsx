'use client';

import { useParams } from 'next/navigation';
import { RemunerationDetail } from './RemunerationDetail';

/** 報酬明細の詳細（報酬タブの下位画面）。戻る導線は外枠の題名帯が出す。 */
export function CastRemunerationDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  return <RemunerationDetail key={orderId} id={orderId} />;
}
