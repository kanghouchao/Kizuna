import type { DefaultValues } from 'react-hook-form';
import { type OrderCreateRequest, toFeeLineInputs } from '@/entities/order';
import type { OrderFormData } from '../ui/OrderForm';
import { acquisitionPayload, optionalTime } from './formValues';

/** 受注フォームの初期値。営業日は呼んだ時点の日付なので、定数ではなく毎回組み立てる。 */
export function orderFormDefaults(): DefaultValues<OrderFormData> {
  return {
    business_contact_permissions: [],
    receptionist_id: '',
    business_date: new Date().toISOString().split('T')[0],
    customer_selection: { mode: 'NONE' },
    contact_snapshot: { name: '', phone_number: '', email: '', line_id: '' },
    pax: 1,
    reception_route: 'PHONE',
    special_service_ids: [],
    course_id: '',
    fee_lines: [],
    acquisition_source: '',
    medium_id: '',
  };
}

/** フォーム値から受注の送信内容を組む。送信先ごとの項目（再提供の元受注・確認トークン）は呼出側が足す。 */
export function toOrderRequestBody({
  acquisition_source,
  medium_id,
  ...data
}: OrderFormData): Omit<OrderCreateRequest, 'replacement_for_order_id' | 'confirmation_token'> {
  return {
    ...data,
    ...acquisitionPayload(acquisition_source, medium_id),
    // 未選択は「自分」の意。項目ごと送らない（OrderCreateRequest.receptionist_id を参照）
    receptionist_id: data.receptionist_id === '' ? undefined : Number(data.receptionist_id),
    arrival_scheduled_start_time: optionalTime(data.arrival_scheduled_start_time ?? ''),
    arrival_scheduled_end_time: optionalTime(data.arrival_scheduled_end_time ?? ''),
    // 人数は未操作なら number、編集後は文字列で届く。空欄は送らない — Number('') の 0 は @Min(1) に撥ねられる
    pax: `${data.pax ?? ''}` === '' ? undefined : Number(data.pax),
    fee_lines: toFeeLineInputs(data.fee_lines),
  };
}
