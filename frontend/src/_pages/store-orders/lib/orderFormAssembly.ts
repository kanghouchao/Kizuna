import type { DefaultValues } from 'react-hook-form';
import {
  type OrderApplicationConfirmationRequest,
  type OrderApplicationDetail,
  type OrderCreateRequest,
  toFeeLineInputs,
} from '@/entities/order';
import type { OrderFormData } from '../ui/OrderForm';
import { acquisitionPayload, optionalTime } from './formValues';

/**
 * 受注フォームの初期値。営業日は呼んだ時点の日付なので、定数ではなく毎回組み立てる。
 * 予約申請の確定では申請内容を事前入力する。ここで直した値は受注にだけ現れ、申請原文は動かない。
 */
export function orderFormDefaults(
  application?: OrderApplicationDetail
): DefaultValues<OrderFormData> {
  const defaults: DefaultValues<OrderFormData> = {
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
    contact_imports: [],
    clear_cast: false,
  };
  if (!application) return defaults;
  return {
    ...defaults,
    business_date: application.business_date ?? defaults.business_date,
    arrival_scheduled_start_time: application.arrival_scheduled_start_time?.slice(0, 5) ?? '',
    pax: application.pax ?? 1,
    cast_id: application.cast_id ?? '',
    remarks: application.remarks ?? '',
    contact_snapshot: {
      ...application.contact_snapshot,
      name: application.contact_snapshot?.name ?? application.requester_declared_name ?? '',
    },
  };
}

/** フォーム値から受注の送信内容を組む。送信先ごとの項目（再提供の元受注・確認トークン）は呼出側が足す。 */
export function toOrderRequestBody({
  acquisition_source,
  medium_id,
  contact_imports: _imports,
  clear_cast: _clearCast,
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

/**
 * 受注の送信内容を予約申請の確定端点の項目へ写す。端点は未知の項目を 400 で拒むので、受注作成だけの
 * 項目（派遣先・受付経路など）はここで落とす。会員申請の顧客は会員の紐づけが決めるため、顧客の決め方と
 * 連絡先の取り込みはゲスト申請でだけ送る。
 */
export function toApplicationConfirmationBody(
  data: OrderFormData,
  isGuest: boolean
): Omit<OrderApplicationConfirmationRequest, 'confirmation_token'> {
  const body = toOrderRequestBody(data);
  return {
    receptionist_id: body.receptionist_id,
    business_date: body.business_date,
    arrival_scheduled_start_time: body.arrival_scheduled_start_time,
    arrival_scheduled_end_time: body.arrival_scheduled_end_time,
    cast_id: data.clear_cast || !body.cast_id ? undefined : body.cast_id,
    pax: body.pax,
    special_service_ids: body.special_service_ids,
    course_id: body.course_id,
    fee_lines: body.fee_lines,
    remarks: body.remarks || undefined,
    acquisition_source: body.acquisition_source,
    medium_id: body.medium_id,
    contact_imports: isGuest ? data.contact_imports : undefined,
    customer_selection: isGuest ? body.customer_selection : undefined,
    contact_snapshot: body.contact_snapshot,
  };
}
