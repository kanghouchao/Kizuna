'use client';

import { useGuestReservationForm } from '../../model/useGuestReservationForm';

/** ゲスト予約申請フォームの画面。検証と送信は useGuestReservationForm が持つ。 */
export default function GuestReservationSection({ storeId }: { storeId: string }) {
  const { field, errors, isSubmitting, onSubmit, consent, failure, acceptedId } =
    useGuestReservationForm(storeId);

  const fieldClass =
    'w-full bg-transparent border px-4 py-3 text-sm text-[var(--storefront-fg)] outline-none';
  const fieldStyle = {
    borderColor: 'color-mix(in srgb, var(--storefront-accent) 25%, transparent)',
  };
  const labelClass =
    'block text-[11px] tracking-[0.2em] mb-2 text-[color-mix(in_srgb,var(--storefront-fg)_55%,transparent)]';
  // 失敗の色もテンプレートのトークンから採る（生の色相を持ち込むとテンプレートごとの配色から外れる）
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
            店舗よりご連絡のうえ、内容を確認して確定いたします。
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
          ご希望の内容とご連絡先をお送りください。店舗よりご連絡のうえ、内容を確認して予約を確定いたします。
        </p>
        {/* noValidate: ブラウザ既定の制約が生きている限りブラウザが submit の手前で止め、
            検証の文言が描かれない。人数の下限は検証の min 規則が引き継ぐ */}
        <form onSubmit={onSubmit} className="space-y-6" noValidate>
          <div>
            <label className={labelClass} htmlFor="guest-contact-name">
              お名前
            </label>
            <input
              id="guest-contact-name"
              aria-invalid={!!errors.contact_name}
              aria-describedby={errors.contact_name ? 'guest-name-error' : undefined}
              type="text"
              className={fieldClass}
              style={fieldStyle}
              {...field('contact_name')}
            />
            {errors.contact_name && (
              <p id="guest-name-error" className={errorClass}>
                {errors.contact_name.message}
              </p>
            )}
          </div>
          <div>
            <label className={labelClass} htmlFor="guest-contact-phone">
              お電話番号
            </label>
            <input
              id="guest-contact-phone"
              aria-invalid={!!errors.contact_phone_number}
              aria-describedby={errors.contact_phone_number ? 'guest-phone-error' : undefined}
              type="tel"
              className={fieldClass}
              style={fieldStyle}
              {...field('contact_phone_number')}
            />
            {errors.contact_phone_number && (
              <p id="guest-phone-error" className={errorClass}>
                {errors.contact_phone_number.message}
              </p>
            )}
          </div>
          <p className="text-sm text-[var(--storefront-fg)]">
            電話・メール・LINEのいずれかをご入力ください。
          </p>
          {(
            [
              ['contact_email', 'メール', 'email'],
              ['contact_line_id', 'LINE ID', 'text'],
            ] as const
          ).map(([key, label, type]) => (
            <div key={key}>
              <label className={labelClass} htmlFor={key}>
                {label}
              </label>
              <input
                id={key}
                type={type}
                aria-invalid={!!errors[key]}
                aria-describedby={errors[key] ? `${key}-error` : undefined}
                className={fieldClass}
                style={fieldStyle}
                {...field(key)}
              />
              {errors[key] && (
                <p id={`${key}-error`} className={errorClass}>
                  {errors[key]?.message}
                </p>
              )}
            </div>
          ))}
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
                {...field('business_date')}
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
                {...field('arrival_scheduled_start_time')}
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
              {...field('pax')}
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
              {...field('remarks')}
            />
            {errors.remarks && <p className={errorClass}>{errors.remarks.message}</p>}
          </div>
          {consent.isLoading ? (
            <p role="status">同意文面を読み込み中...</p>
          ) : consent.failure ? (
            <div role="alert">
              <p>同意文面を取得できませんでした。</p>
              <button type="button" onClick={() => void consent.reload()}>
                再試行
              </button>
            </div>
          ) : (
            consent.data && (
              <fieldset className="space-y-4 text-sm text-[var(--storefront-fg)]">
                <legend className="mb-3">連絡への同意</legend>
                <label className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    required
                    className="mt-1 shrink-0"
                    aria-invalid={!!errors.business_allowed}
                    aria-describedby={errors.business_allowed ? 'guest-consent-error' : undefined}
                    {...field('business_allowed')}
                  />
                  <span>{consent.data.business_text}（必須）</span>
                </label>
                {errors.business_allowed && (
                  <p id="guest-consent-error" className={errorClass}>
                    {errors.business_allowed.message}
                  </p>
                )}
                <label className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    className="mt-1 shrink-0"
                    {...field('marketing_allowed')}
                  />
                  <span>{consent.data.marketing_text}</span>
                </label>
              </fieldset>
            )
          )}
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
