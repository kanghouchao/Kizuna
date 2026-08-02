'use client';

import { useForm } from 'react-hook-form';

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
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-7">
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
          {...register('display_name', { required: true, maxLength: 150 })}
        />
        <span className="auth-field__accent" />
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
          {...register('email', { required: true, maxLength: 127 })}
        />
        <span className="auth-field__accent" />
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
            {...register('consent', { required: true })}
          />
          <span>利用規約およびプライバシーポリシーに同意します</span>
        </label>
        {errors.consent && (
          <p className="text-xs text-[#dc2626]">同意いただける場合のみ登録できます</p>
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
