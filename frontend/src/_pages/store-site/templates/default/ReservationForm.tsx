'use client';

import type { HTMLInputTypeAttribute } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { useGuestReservationForm } from '../../model/useGuestReservationForm';

interface FieldProps {
  id: string;
  label: string;
  type?: HTMLInputTypeAttribute;
  min?: number;
  error?: string;
  registration: UseFormRegisterReturn;
}

/** 一行の入力欄。誤りは欄のすぐ下に出し、読み上げでも欄に結び付ける。 */
function Field({ id, label, type = 'text', min, error, registration }: FieldProps) {
  return (
    <div className="sfd-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        min={min}
        aria-invalid={!!error}
        aria-describedby={error ? `${id}-error` : undefined}
        {...registration}
      />
      {error && (
        <p id={`${id}-error`} className="sfd-error">
          {error}
        </p>
      )}
    </div>
  );
}

/** ゲスト予約申請フォームの画面。検証と送信は useGuestReservationForm が持つ。 */
export default function ReservationForm({ storeId }: { storeId: string }) {
  const { field, errors, busy, onSubmit, consent, failure, acceptedId } =
    useGuestReservationForm(storeId);

  if (acceptedId !== null) {
    return (
      <div className="sfd-accepted">
        <p className="sfd-accepted-title">ご予約の希望を承りました</p>
        <p>
          この時点ではまだ予約は確定しておりません。
          <br />
          店舗よりご連絡のうえ、内容を確認して確定いたします。
        </p>
        <p className="sfd-accepted-id">受付番号: {acceptedId}</p>
      </div>
    );
  }

  return (
    // noValidate: ブラウザの制約が生きていると submit の手前で止まり、検証の文言が描かれない
    <form onSubmit={onSubmit} className="sfd-form" noValidate>
      <p className="sfd-form-lead">
        ご希望の内容とご連絡先をお送りください。店舗よりご連絡のうえ、内容を確認して予約を確定いたします。
      </p>

      <Field
        id="sfd-contact-name"
        label="お名前"
        error={errors.contact_name?.message}
        registration={field('contact_name')}
      />

      <fieldset className="sfd-fieldset">
        <legend>ご連絡先</legend>
        <p className="sfd-form-hint">電話・メール・LINEのいずれかをご入力ください。</p>
        <Field
          id="sfd-contact-phone"
          label="お電話番号"
          type="tel"
          error={errors.contact_phone_number?.message}
          registration={field('contact_phone_number')}
        />
        <Field
          id="sfd-contact-email"
          label="メール"
          type="email"
          error={errors.contact_email?.message}
          registration={field('contact_email')}
        />
        <Field
          id="sfd-contact-line"
          label="LINE ID"
          error={errors.contact_line_id?.message}
          registration={field('contact_line_id')}
        />
      </fieldset>

      <div className="sfd-field-pair">
        <Field
          id="sfd-business-date"
          label="ご希望日"
          type="date"
          error={errors.business_date?.message}
          registration={field('business_date')}
        />
        <Field
          id="sfd-arrival-time"
          label="ご希望時刻"
          type="time"
          registration={field('arrival_scheduled_start_time')}
        />
      </div>
      <Field
        id="sfd-pax"
        label="ご人数"
        type="number"
        min={1}
        error={errors.pax?.message}
        registration={field('pax')}
      />
      <div className="sfd-field">
        <label htmlFor="sfd-remarks">ご要望（任意）</label>
        <textarea
          id="sfd-remarks"
          rows={4}
          aria-invalid={!!errors.remarks}
          aria-describedby={errors.remarks ? 'sfd-remarks-error' : undefined}
          {...field('remarks')}
        />
        {errors.remarks && (
          <p id="sfd-remarks-error" className="sfd-error">
            {errors.remarks.message}
          </p>
        )}
      </div>

      {consent.isLoading ? (
        <p role="status" className="sfd-form-hint">
          同意文面を読み込み中...
        </p>
      ) : consent.failure ? (
        <div role="alert" className="sfd-consent-failure">
          <p className="sfd-error">同意文面を取得できませんでした。</p>
          <button
            type="button"
            className="sfd-btn sfd-btn-quiet"
            onClick={() => void consent.reload()}
          >
            再試行
          </button>
        </div>
      ) : (
        consent.data && (
          <fieldset className="sfd-fieldset sfd-consent">
            <legend>連絡への同意</legend>
            <label className="sfd-check">
              <input
                type="checkbox"
                required
                aria-invalid={!!errors.business_allowed}
                aria-describedby={errors.business_allowed ? 'sfd-consent-error' : undefined}
                {...field('business_allowed')}
              />
              <span>{consent.data.business_text}（必須）</span>
            </label>
            {errors.business_allowed && (
              <p id="sfd-consent-error" className="sfd-error">
                {errors.business_allowed.message}
              </p>
            )}
            <label className="sfd-check">
              <input type="checkbox" {...field('marketing_allowed')} />
              <span>{consent.data.marketing_text}</span>
            </label>
          </fieldset>
        )
      )}

      {failure && (
        <p role="alert" className="sfd-error">
          {failure}
        </p>
      )}
      {/* 検証では塞がない。灰色のボタンは何が足りないかを言わないが、押せば欄の傍が言う */}
      <button type="submit" disabled={busy} className="sfd-btn sfd-form-submit">
        {busy ? '送信中...' : 'この内容で予約を希望する'}
      </button>
    </form>
  );
}
