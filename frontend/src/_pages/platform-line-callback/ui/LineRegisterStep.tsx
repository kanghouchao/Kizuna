'use client';

import { useForm } from 'react-hook-form';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE } from '@/shared/lib';

export interface LineRegisterValues {
  display_name: string;
  email: string;
  consent: boolean;
}

interface LineRegisterStepProps {
  defaultDisplayName: string;
  onSubmit: (values: LineRegisterValues) => Promise<void>;
}

/**
 * 未登録の LINE アカウントに対する会員登録の確認段階。
 * 同意チェックは画面側の関門で、サーバーへは送信しない。
 */
export default function LineRegisterStep({ defaultDisplayName, onSubmit }: LineRegisterStepProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LineRegisterValues>({
    defaultValues: { display_name: defaultDisplayName, email: '', consent: false },
  });

  return (
    // noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
    // 永久に描かれない。type="email" の執行もここで止まるため、下の pattern が引き継ぐ。
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-7" noValidate>
      <div className="auth-field">
        <label
          htmlFor="line-register-display-name"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          表示名
        </label>
        <input
          id="line-register-display-name"
          type="text"
          required
          maxLength={150}
          className="auth-field__input"
          placeholder="表示名を入力"
          aria-invalid={!!errors.display_name}
          aria-describedby={errors.display_name ? 'line-register-display-name-error' : undefined}
          {...register('display_name', {
            required: '表示名を入力してください',
            maxLength: { value: 150, message: '表示名は150文字以内で入力してください' },
          })}
        />
        <span className="auth-field__accent" />
        {errors.display_name && (
          <p id="line-register-display-name-error" className="auth-field__error">
            {errors.display_name.message}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label
          htmlFor="line-register-email"
          className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
        >
          メールアドレス
        </label>
        <input
          id="line-register-email"
          type="email"
          autoComplete="email"
          required
          maxLength={127}
          className="auth-field__input"
          placeholder="example@mail.com"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'line-register-email-error' : undefined}
          {...register('email', {
            required: 'メールアドレスを入力してください',
            pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
            maxLength: { value: 127, message: 'メールアドレスは127文字以内で入力してください' },
          })}
        />
        <span className="auth-field__accent" />
        {errors.email && (
          <p id="line-register-email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <label
          htmlFor="line-register-consent"
          className="flex items-start gap-3 text-xs text-[#6b6660] leading-relaxed"
        >
          <input
            id="line-register-consent"
            type="checkbox"
            className="auth-check mt-0.5"
            required
            aria-invalid={!!errors.consent}
            aria-describedby={errors.consent ? 'line-register-consent-error' : undefined}
            {...register('consent', { required: '同意いただける場合のみ登録できます' })}
          />
          <span>利用規約およびプライバシーポリシーに同意します</span>
        </label>
        {errors.consent && (
          <p id="line-register-consent-error" className="auth-field__error">
            {errors.consent.message}
          </p>
        )}
      </div>

      <div className="pt-2">
        <button type="submit" disabled={isSubmitting} className="auth-btn">
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2.5">
              <span className="auth-spinner" />
              登録中...
            </span>
          ) : (
            '登録する'
          )}
        </button>
      </div>
    </form>
  );
}
