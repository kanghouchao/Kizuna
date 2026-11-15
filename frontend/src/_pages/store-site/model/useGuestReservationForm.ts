'use client';

import { useState } from 'react';
import { useForm, type FieldPath, type RegisterOptions } from 'react-hook-form';
import { guestOrderApplicationApi } from '@/entities/order';
import {
  getApiErrorMessage,
  useResource,
  isConflict,
  integerRule,
  EMAIL_PATTERN,
  EMAIL_PATTERN_MESSAGE,
} from '@/shared/lib';

export interface GuestReservationFormValues {
  business_date: string;
  arrival_scheduled_start_time: string;
  pax: number;
  contact_name: string;
  contact_phone_number: string;
  contact_email: string;
  contact_line_id: string;
  remarks: string;
  business_allowed: boolean;
  marketing_allowed: boolean;
}

type Rules = {
  [K in FieldPath<GuestReservationFormValues>]?: RegisterOptions<GuestReservationFormValues, K>;
};

/**
 * ゲスト予約申請の検証と送信。未ログインの来訪者が連絡先と希望内容を送る唯一の入口で、送信先は
 * 匿名の端点、店舗はサーバが訪問されたドメインから解決した id を受け取って名乗る。
 *
 * 申請は予約の成立ではない。acceptedId が入った後は、折返しを待つことだけを伝える画面にする。
 */
export function useGuestReservationForm(storeId: string) {
  const consent = useResource(guestOrderApplicationApi.consent);
  const [acceptedId, setAcceptedId] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    setValue,
    getValues,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<GuestReservationFormValues>({
    defaultValues: {
      business_date: '',
      arrival_scheduled_start_time: '',
      pax: 1,
      contact_name: '',
      contact_phone_number: '',
      contact_email: '',
      contact_line_id: '',
      remarks: '',
      business_allowed: false,
      marketing_allowed: false,
    },
  });

  const rules: Rules = {
    contact_name: {
      required: 'お名前をご入力ください',
      maxLength: { value: 255, message: 'お名前は 255 文字以内です' },
    },
    contact_phone_number: {
      maxLength: { value: 50, message: '電話番号は 50 文字以内です' },
      validate: value =>
        [value, getValues('contact_email'), getValues('contact_line_id')].some(v => v.trim()) ||
        '電話・メール・LINEのいずれかをご入力ください',
    },
    contact_email: {
      maxLength: { value: 254, message: 'メールは254文字以内です' },
      validate: value => !value.trim() || EMAIL_PATTERN.test(value.trim()) || EMAIL_PATTERN_MESSAGE,
    },
    contact_line_id: {
      maxLength: { value: 255, message: 'LINE IDは255文字以内です' },
    },
    business_date: { required: 'ご希望日を選択してください' },
    pax: {
      required: 'ご人数をご入力ください',
      min: { value: 1, message: 'ご人数は 1 名以上です' },
      // フォームは noValidate で、type="number" の暗黙の step=1 も効かない。これが無いと 1.5 が届く
      validate: integerRule('ご人数'),
    },
    remarks: { maxLength: { value: 500, message: 'ご要望は 500 文字以内です' } },
    business_allowed: {
      validate: value => value || '今回の予約に関する業務連絡への同意が必要です',
    },
  };

  const field = <K extends FieldPath<GuestReservationFormValues>>(name: K) =>
    register(name, rules[name] as RegisterOptions<GuestReservationFormValues, K> | undefined);

  const submit = async (values: GuestReservationFormValues) => {
    setFailure(null);
    if (!consent.data || consent.failure || consent.isLoading) {
      setFailure('同意文面を取得してから送信してください');
      return;
    }
    try {
      const accepted = await guestOrderApplicationApi.request(storeId, {
        contact_consent: {
          version: consent.data.version,
          business_allowed: values.business_allowed,
          marketing_allowed: values.marketing_allowed,
        },
        business_date: values.business_date,
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        pax: Number(values.pax),
        contact_snapshot: {
          name: values.contact_name,
          phone_number: values.contact_phone_number,
          email: values.contact_email,
          line_id: values.contact_line_id,
        },
        remarks: values.remarks || undefined,
      });
      setAcceptedId(accepted.id);
    } catch (error) {
      if (isConflict(error)) {
        setValue('business_allowed', false);
        setValue('marketing_allowed', false);
        await consent.reload();
      }
      // 流量制限・希望日の範囲外など、サーバは対処の分かる文言を返す。汎用文言に潰さない
      setFailure(
        getApiErrorMessage(error, 'ご予約の送信に失敗しました。時間をおいてお試しください')
      );
    }
  };

  return {
    /** 入力欄へ渡す登録。検証の規則はここが持ち、画面は欄の名前だけを渡す。 */
    field,
    errors,
    isSubmitting,
    onSubmit: handleSubmit(submit),
    consent,
    failure,
    acceptedId,
  };
}
