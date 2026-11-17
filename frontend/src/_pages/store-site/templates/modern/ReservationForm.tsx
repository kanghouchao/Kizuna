'use client';

import { CircleAlertIcon, PartyPopperIcon, SendIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { useGuestReservationForm } from '../../model/useGuestReservationForm';

function Field({
  id,
  label,
  required,
  error,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      {/* 「必須」の札は label の外に置き、欄の名前を label の文字だけにする */}
      <div className="mb-1.5 flex items-center gap-2">
        <label htmlFor={id} className="text-sm font-bold">
          {label}
        </label>
        {required && (
          <span className="m-bg-pink rounded-full px-2 text-[11px] leading-5 font-bold">必須</span>
        )}
      </div>
      {children}
      {error && <FieldError id={`${id}-error`}>{error}</FieldError>}
    </div>
  );
}

function FieldError({ id, children }: { id?: string; children: ReactNode }) {
  return (
    <p id={id} className="m-error mt-1.5 flex items-start gap-1 text-sm font-bold">
      <CircleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>{children}</span>
    </p>
  );
}

const card = 'm-bg-card m-sticker rounded-[var(--m-radius)]';

/** ゲスト予約申請フォームの画面（modern テンプレート）。検証と送信は useGuestReservationForm が持つ。 */
export default function ReservationForm({ storeId }: { storeId: string }) {
  const { field, errors, busy, onSubmit, consent, failure, acceptedId } =
    useGuestReservationForm(storeId);

  if (acceptedId !== null) {
    return (
      // 送信ボタンが消えるので、受付の案内へフォーカスを移して読み上げさせる
      <div
        role="status"
        tabIndex={-1}
        ref={el => el?.focus()}
        className={`${card} m-pop p-8 text-center outline-none`}
      >
        <span className="m-bg-mint m-border mx-auto grid size-14 place-items-center rounded-full">
          <PartyPopperIcon className="size-7" aria-hidden />
        </span>
        <p className="m-display mt-4 text-2xl">ご予約の希望を承りました</p>
        <p className="mt-4 text-[15px] leading-8">
          この時点ではまだ予約は確定しておりません。
          <br />
          店舗よりご連絡のうえ、内容を確認して確定いたします。
        </p>
        <p className="m-bg-yellow m-border mt-5 inline-block rounded-full px-4 py-1 text-sm font-bold break-all">
          受付番号: {acceptedId}
        </p>
      </div>
    );
  }

  const describedBy = (id: string, error: unknown) => (error ? `${id}-error` : undefined);

  return (
    <div className={`${card} min-w-0 p-5 sm:p-8`}>
      <p className="text-[15px] leading-8">
        ご希望の内容とご連絡先をお送りください。店舗よりご連絡のうえ、内容を確認して予約を確定いたします。
      </p>
      {/* noValidate: ブラウザ既定の制約が生きている限りブラウザが submit の手前で止め、
          検証の文言が描かれない。人数の下限は検証の min 規則が引き継ぐ */}
      <form onSubmit={onSubmit} className="mt-6 space-y-5" noValidate>
        <Field id="guest-contact-name" label="お名前" required error={errors.contact_name?.message}>
          <input
            id="guest-contact-name"
            aria-required="true"
            type="text"
            autoComplete="name"
            className="m-field"
            aria-invalid={!!errors.contact_name}
            aria-describedby={describedBy('guest-contact-name', errors.contact_name)}
            {...field('contact_name')}
          />
        </Field>

        <fieldset className="rounded-[20px] border-2 border-dashed border-[var(--m-ink)] p-4">
          <legend className="px-2 text-sm font-bold">ご連絡先</legend>
          <p className="m-sub text-sm">電話・メール・LINEのいずれかをご入力ください。</p>
          <div className="mt-3 space-y-4">
            <Field
              id="guest-contact-phone"
              label="お電話番号"
              error={errors.contact_phone_number?.message}
            >
              <input
                id="guest-contact-phone"
                type="tel"
                autoComplete="tel"
                className="m-field"
                aria-invalid={!!errors.contact_phone_number}
                aria-describedby={describedBy('guest-contact-phone', errors.contact_phone_number)}
                {...field('contact_phone_number')}
              />
            </Field>
            <Field id="guest-contact-email" label="メール" error={errors.contact_email?.message}>
              <input
                id="guest-contact-email"
                type="email"
                autoComplete="email"
                className="m-field"
                aria-invalid={!!errors.contact_email}
                aria-describedby={describedBy('guest-contact-email', errors.contact_email)}
                {...field('contact_email')}
              />
            </Field>
            <Field id="guest-contact-line" label="LINE ID" error={errors.contact_line_id?.message}>
              <input
                id="guest-contact-line"
                type="text"
                className="m-field"
                aria-invalid={!!errors.contact_line_id}
                aria-describedby={describedBy('guest-contact-line', errors.contact_line_id)}
                {...field('contact_line_id')}
              />
            </Field>
          </div>
        </fieldset>

        <div className="grid gap-5 sm:grid-cols-2">
          <Field
            id="guest-business-date"
            label="ご希望日"
            required
            error={errors.business_date?.message}
          >
            <input
              id="guest-business-date"
              aria-required="true"
              type="date"
              className="m-field"
              aria-invalid={!!errors.business_date}
              aria-describedby={describedBy('guest-business-date', errors.business_date)}
              {...field('business_date')}
            />
          </Field>
          <Field id="guest-arrival-time" label="ご希望時刻">
            <input
              id="guest-arrival-time"
              type="time"
              className="m-field"
              {...field('arrival_scheduled_start_time')}
            />
          </Field>
        </div>

        <Field id="guest-pax" label="ご人数" required error={errors.pax?.message}>
          <input
            id="guest-pax"
            aria-required="true"
            type="number"
            min={1}
            inputMode="numeric"
            className="m-field sm:max-w-40"
            aria-invalid={!!errors.pax}
            aria-describedby={describedBy('guest-pax', errors.pax)}
            {...field('pax')}
          />
        </Field>

        <Field id="guest-remarks" label="ご要望（任意）" error={errors.remarks?.message}>
          <textarea
            id="guest-remarks"
            rows={4}
            className="m-field"
            aria-invalid={!!errors.remarks}
            aria-describedby={describedBy('guest-remarks', errors.remarks)}
            {...field('remarks')}
          />
        </Field>

        {consent.isLoading ? (
          <p role="status" className="m-sub text-sm">
            同意文面を読み込み中...
          </p>
        ) : consent.failure ? (
          <div
            role="alert"
            className="rounded-[20px] border-2 border-dashed border-[var(--m-ink)] p-4"
          >
            <p className="text-sm font-bold">同意文面を取得できませんでした。</p>
            <button
              type="button"
              onClick={() => void consent.reload()}
              className="m-bg-yellow m-border mt-3 rounded-full px-4 py-1.5 text-sm font-bold"
            >
              再試行
            </button>
          </div>
        ) : (
          consent.data && (
            <fieldset className="space-y-3 text-sm">
              <legend className="mb-2 font-bold">連絡への同意</legend>
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  required
                  className="m-check"
                  aria-invalid={!!errors.business_allowed}
                  aria-describedby={describedBy('guest-consent', errors.business_allowed)}
                  {...field('business_allowed')}
                />
                <span>{consent.data.business_text}（必須）</span>
              </label>
              {errors.business_allowed && (
                <FieldError id="guest-consent-error">{errors.business_allowed.message}</FieldError>
              )}
              <label className="flex items-start gap-3">
                <input type="checkbox" className="m-check" {...field('marketing_allowed')} />
                <span>{consent.data.marketing_text}</span>
              </label>
            </fieldset>
          )
        )}

        {failure && (
          <div role="alert">
            <FieldError>{failure}</FieldError>
          </div>
        )}
        {/* 検証では塞がない。灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
        <button
          type="submit"
          disabled={busy}
          className="m-bg-pink m-sticker m-lift flex w-full items-center justify-center gap-2 rounded-full px-6 py-4 text-lg font-bold disabled:opacity-60"
        >
          <SendIcon className="size-5 shrink-0" aria-hidden />
          {busy ? '送信中...' : 'この内容で予約を希望する'}
        </button>
      </form>
    </div>
  );
}
