'use client';

import type { ReactNode } from 'react';
import { useGuestReservationForm } from '../../model/useGuestReservationForm';

interface RowProps {
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}

/** 帳面の一行: 左に項目名、右に記入欄。誤りは欄のすぐ下に出す。 */
function Row({ id, label, error, children }: RowProps) {
  return (
    <div className="c-form-row">
      <label htmlFor={id}>{label}</label>
      <div className="c-form-field">
        {children}
        {error && (
          <p id={`${id}-error`} className="c-form-error">
            {error}
          </p>
        )}
      </div>
    </div>
  );
}

/** 記入欄に渡す、誤りの有無と誤りの文の結び付け。 */
const invalid = (id: string, error: unknown) => ({
  id,
  'aria-invalid': !!error,
  'aria-describedby': error ? `${id}-error` : undefined,
});

/** ゲスト予約申請フォームの画面。検証と送信は useGuestReservationForm が持つ。 */
export default function ReservationForm({ storeId }: { storeId: string }) {
  const { field, errors, busy, onSubmit, consent, failure, acceptedId } =
    useGuestReservationForm(storeId);

  if (acceptedId !== null) {
    return (
      <div className="c-accepted" role="status">
        <p className="c-accepted-title c-display">ご予約の希望を承りました</p>
        <p>
          この時点ではまだ予約は確定しておりません。
          <br />
          店舗よりご連絡のうえ、内容を確認して確定いたします。
        </p>
        <p className="c-quiet">受付番号: {acceptedId}</p>
      </div>
    );
  }

  return (
    <div className="c-form">
      <p className="c-form-lead">
        ご希望の内容とご連絡先をお送りください。店舗よりご連絡のうえ、内容を確認して予約を確定いたします。
      </p>
      {/* noValidate: ブラウザ既定の制約が生きている限りブラウザが submit の手前で止め、
          検証の文言が描かれない。人数の下限は検証の min 規則が引き継ぐ */}
      <form onSubmit={onSubmit} noValidate>
        <Row id="guest-contact-name" label="お名前" error={errors.contact_name?.message}>
          <input
            type="text"
            autoComplete="name"
            {...invalid('guest-contact-name', errors.contact_name)}
            {...field('contact_name')}
          />
        </Row>

        <p className="c-form-hint">電話・メール・LINEのいずれかをご入力ください。</p>
        <Row
          id="guest-contact-phone"
          label="お電話番号"
          error={errors.contact_phone_number?.message}
        >
          <input
            type="tel"
            autoComplete="tel"
            {...invalid('guest-contact-phone', errors.contact_phone_number)}
            {...field('contact_phone_number')}
          />
        </Row>
        <Row id="contact_email" label="メール" error={errors.contact_email?.message}>
          <input
            type="email"
            autoComplete="email"
            {...invalid('contact_email', errors.contact_email)}
            {...field('contact_email')}
          />
        </Row>
        <Row id="contact_line_id" label="LINE ID" error={errors.contact_line_id?.message}>
          <input
            type="text"
            {...invalid('contact_line_id', errors.contact_line_id)}
            {...field('contact_line_id')}
          />
        </Row>

        <Row id="guest-business-date" label="ご希望日" error={errors.business_date?.message}>
          <input
            type="date"
            {...invalid('guest-business-date', errors.business_date)}
            {...field('business_date')}
          />
        </Row>
        <Row id="guest-arrival-time" label="ご希望時刻">
          <input id="guest-arrival-time" type="time" {...field('arrival_scheduled_start_time')} />
        </Row>
        <Row id="guest-pax" label="ご人数" error={errors.pax?.message}>
          <input
            type="number"
            min={1}
            inputMode="numeric"
            className="c-form-short"
            {...invalid('guest-pax', errors.pax)}
            {...field('pax')}
          />
        </Row>
        <Row id="guest-remarks" label="ご要望（任意）" error={errors.remarks?.message}>
          <textarea rows={4} {...invalid('guest-remarks', errors.remarks)} {...field('remarks')} />
        </Row>

        {consent.isLoading ? (
          <p role="status" className="c-quiet">
            同意文面を読み込み中...
          </p>
        ) : consent.failure ? (
          <div role="alert" className="c-form-consent-failure">
            <p>同意文面を取得できませんでした。</p>
            <button type="button" className="c-form-retry" onClick={() => void consent.reload()}>
              再試行
            </button>
          </div>
        ) : (
          consent.data && (
            <fieldset className="c-form-consent">
              <legend>連絡への同意</legend>
              <label>
                <input
                  type="checkbox"
                  required
                  aria-invalid={!!errors.business_allowed}
                  aria-describedby={errors.business_allowed ? 'guest-consent-error' : undefined}
                  {...field('business_allowed')}
                />
                <span>{consent.data.business_text}（必須）</span>
              </label>
              {errors.business_allowed && (
                <p id="guest-consent-error" className="c-form-error">
                  {errors.business_allowed.message}
                </p>
              )}
              <label>
                <input type="checkbox" {...field('marketing_allowed')} />
                <span>{consent.data.marketing_text}</span>
              </label>
            </fieldset>
          )
        )}

        {failure && (
          <p role="alert" className="c-form-error c-form-failure">
            {failure}
          </p>
        )}
        {/* 検証では塞がない — 灰色のボタンは何が足りないかを言わない。押せば欄の傍が言う */}
        <button type="submit" disabled={busy} className="c-form-submit">
          {busy ? '送信中...' : 'この内容で予約を希望する'}
        </button>
      </form>
    </div>
  );
}
