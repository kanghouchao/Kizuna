'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { guestOrderApplicationApi } from '@/entities/order';
import { getApiErrorMessage, integerRule } from '@/shared/lib';

interface GuestReservationFormValues {
  business_date: string;
  arrival_scheduled_start_time: string;
  pax: number;
  contact_name: string;
  contact_phone_number: string;
  remarks: string;
}

/**
 * 公開店面のゲスト予約申請フォーム。未ログインの来訪者が連絡先と希望内容を送る唯一の入口で、
 * 送信先は匿名の端点。店舗は訪問された域名から解決されるため、画面は店舗を名乗らない。
 *
 * 申請は予約の成立ではない。店舗が折返し連絡で内容を詰めてから確定するため、送信後は
 * 折返しを待つことだけを伝える（申請を読み返す口は匿名の来訪者には無い）。
 */
export default function GuestReservationSection() {
  const [acceptedId, setAcceptedId] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<GuestReservationFormValues>({
    defaultValues: {
      business_date: '',
      arrival_scheduled_start_time: '',
      pax: 1,
      contact_name: '',
      contact_phone_number: '',
      remarks: '',
    },
  });

  const submit = async (values: GuestReservationFormValues) => {
    setFailure(null);
    try {
      const accepted = await guestOrderApplicationApi.request({
        business_date: values.business_date,
        arrival_scheduled_start_time: values.arrival_scheduled_start_time || undefined,
        pax: Number(values.pax),
        contact_name: values.contact_name,
        contact_phone_number: values.contact_phone_number,
        remarks: values.remarks || undefined,
      });
      setAcceptedId(accepted.id);
    } catch (error) {
      // 流量制限・希望日の範囲外など、サーバは対処の分かる文言を返す。汎用文言に潰さない
      setFailure(
        getApiErrorMessage(error, 'ご予約の送信に失敗しました。時間をおいてお試しください')
      );
    }
  };

  const fieldClass =
    'w-full bg-transparent border px-4 py-3 text-sm text-[var(--storefront-fg)] outline-none';
  const fieldStyle = {
    borderColor: 'color-mix(in srgb, var(--storefront-accent) 25%, transparent)',
  };
  const labelClass =
    'block text-[11px] tracking-[0.2em] mb-2 text-[color-mix(in_srgb,var(--storefront-fg)_55%,transparent)]';
  // 失敗の色も模版のトークンから採る（生の色相を持ち込むと模版ごとの配色から外れる）
  const errorClass =
    'mt-2 text-xs text-[color-mix(in_srgb,var(--storefront-danger)_60%,var(--storefront-fg))]';

  if (acceptedId !== null) {
    return (
      <section className="px-5 sm:px-6 pb-20 md:pb-28">
        <div
          className="mx-auto max-w-xl border p-8 text-center"
          style={{ borderColor: 'color-mix(in srgb, var(--storefront-accent) 25%, transparent)' }}
        >
          <p
            className="text-base tracking-[0.2em] text-[var(--storefront-accent)]"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            ご予約の希望を承りました
          </p>
          <p className="mt-5 text-sm leading-7 text-[color-mix(in_srgb,var(--storefront-fg)_65%,transparent)]">
            この時点ではまだ予約は確定しておりません。
            <br />
            店舗よりお電話でご連絡のうえ、内容を確認して確定いたします。
          </p>
          <p className="mt-5 text-xs tracking-wider text-[color-mix(in_srgb,var(--storefront-fg)_40%,transparent)]">
            受付番号: {acceptedId}
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 sm:px-6 pb-20 md:pb-28">
      <div
        className="mx-auto max-w-xl border p-6 md:p-8"
        style={{ borderColor: 'color-mix(in srgb, var(--storefront-accent) 25%, transparent)' }}
      >
        <p className="mb-8 text-sm leading-7 text-[color-mix(in_srgb,var(--storefront-fg)_60%,transparent)]">
          ご希望の内容とご連絡先をお送りください。店舗よりお電話でご連絡のうえ、内容を確認して予約を確定いたします。
        </p>
        {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
            我々の文言は永久に描かれない。人数の下限は下の min 規則が引き継ぐ */}
        <form onSubmit={handleSubmit(submit)} className="space-y-6" noValidate>
          <div>
            <label className={labelClass} htmlFor="guest-contact-name">
              お名前
            </label>
            <input
              id="guest-contact-name"
              type="text"
              className={fieldClass}
              style={fieldStyle}
              {...register('contact_name', {
                required: 'お名前をご入力ください',
                maxLength: { value: 255, message: 'お名前は 255 文字以内です' },
              })}
            />
            {errors.contact_name && <p className={errorClass}>{errors.contact_name.message}</p>}
          </div>
          <div>
            <label className={labelClass} htmlFor="guest-contact-phone">
              お電話番号
            </label>
            <input
              id="guest-contact-phone"
              type="tel"
              className={fieldClass}
              style={fieldStyle}
              {...register('contact_phone_number', {
                required: 'お電話番号をご入力ください',
                maxLength: { value: 50, message: '電話番号は 50 文字以内です' },
              })}
            />
            {errors.contact_phone_number && (
              <p className={errorClass}>{errors.contact_phone_number.message}</p>
            )}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelClass} htmlFor="guest-business-date">
                ご希望日
              </label>
              <input
                id="guest-business-date"
                type="date"
                className={fieldClass}
                style={fieldStyle}
                {...register('business_date', { required: 'ご希望日を選択してください' })}
              />
              {errors.business_date && <p className={errorClass}>{errors.business_date.message}</p>}
            </div>
            <div>
              <label className={labelClass} htmlFor="guest-arrival-time">
                ご希望時刻
              </label>
              <input
                id="guest-arrival-time"
                type="time"
                className={fieldClass}
                style={fieldStyle}
                {...register('arrival_scheduled_start_time')}
              />
            </div>
          </div>
          <div>
            <label className={labelClass} htmlFor="guest-pax">
              ご人数
            </label>
            <input
              id="guest-pax"
              type="number"
              min={1}
              className={fieldClass}
              style={fieldStyle}
              {...register('pax', {
                required: 'ご人数をご入力ください',
                min: { value: 1, message: 'ご人数は 1 名以上です' },
                // noValidate は type="number" の暗黙の step=1 も止める。これが無いと 1.5 が届く
                validate: integerRule('ご人数'),
              })}
            />
            {errors.pax && <p className={errorClass}>{errors.pax.message}</p>}
          </div>
          <div>
            <label className={labelClass} htmlFor="guest-remarks">
              ご要望（任意）
            </label>
            <textarea
              id="guest-remarks"
              rows={4}
              className={fieldClass}
              style={fieldStyle}
              {...register('remarks', {
                maxLength: { value: 500, message: 'ご要望は 500 文字以内です' },
              })}
            />
            {errors.remarks && <p className={errorClass}>{errors.remarks.message}</p>}
          </div>
          {failure && (
            <p role="alert" className={errorClass}>
              {failure}
            </p>
          )}
          {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full text-[11px] tracking-[0.3em] bg-[var(--storefront-accent)] text-[var(--storefront-bg)] px-6 py-4 hover:opacity-80 transition-opacity duration-300 disabled:opacity-50"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            {isSubmitting ? '送信中...' : 'この内容で予約を希望する'}
          </button>
        </form>
      </div>
    </section>
  );
}
