'use client';

import { useForm } from 'react-hook-form';
import { useRouter } from 'next/navigation';
import { notify } from '@/shared/notify';
import { authApi, initialSetupApi, InitialSetupRequest } from '@/entities/user';
import { completeLogin } from '@/features/login';
import { EMAIL_PATTERN, EMAIL_PATTERN_MESSAGE, getApiErrorMessage, isNotFound } from '@/shared/lib';

interface InitialSetupFormValues extends InitialSetupRequest {
  confirm_password: string;
}

/** 最初の管理者の登録フォーム。登録後はそのままログインして管理画面へ遷移する。 */
export function InitialSetupForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<InitialSetupFormValues>({
    defaultValues: { email: '', display_name: '', password: '', confirm_password: '' },
  });

  const submit = async (values: InitialSetupFormValues) => {
    const { email, password, display_name } = values;
    try {
      await initialSetupApi.register({ email, password, display_name });
    } catch (error) {
      // 画面を開いた後に別の誰かが先に登録を済ませた場合
      if (isNotFound(error)) {
        notify.error('初期設定は完了しています');
        router.push('/auth/login');
        return;
      }
      notify.error(getApiErrorMessage(error, '登録に失敗しました'));
      return;
    }
    try {
      const completion = await completeLogin(await authApi.login({ email, password }));
      if (completion.status === 'ok') {
        router.push(completion.path);
        return;
      }
    } catch {
      // 登録自体は完了しているため、自動ログインできなかった分はログイン画面から再開させる。
    }
    notify.success('登録が完了しました。ログインしてください');
    router.push('/auth/login');
  };

  return (
    // noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、我々の文言は
    // 永久に描かれない。type="email" の執行もここで止まるため、下の pattern が引き継ぐ。
    <form onSubmit={handleSubmit(submit)} className="space-y-7" noValidate>
      {/* 文言は .auth-field の外に置く。中に入れると下線（bottom:0 の絶対配置）が
          伸びた入れ物の底へ移り、文言を貫いて描かれる */}
      <div>
        <div className="auth-field">
          <label
            htmlFor="initial-setup-email"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            メールアドレス
          </label>
          <input
            id="initial-setup-email"
            type="email"
            autoComplete="email"
            required
            maxLength={127}
            className="auth-field__input"
            placeholder="example@mail.com"
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? 'initial-setup-email-error' : undefined}
            {...register('email', {
              required: 'メールアドレスを入力してください',
              pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
              maxLength: { value: 127, message: 'メールアドレスは127文字以内で入力してください' },
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.email && (
          <p id="initial-setup-email-error" className="auth-field__error">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
        <div className="auth-field">
          <label
            htmlFor="initial-setup-display-name"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            表示名
          </label>
          <input
            id="initial-setup-display-name"
            type="text"
            required
            maxLength={150}
            className="auth-field__input"
            placeholder="表示名を入力"
            aria-invalid={!!errors.display_name}
            aria-describedby={errors.display_name ? 'initial-setup-display-name-error' : undefined}
            {...register('display_name', {
              required: '表示名を入力してください',
              maxLength: { value: 150, message: '表示名は150文字以内で入力してください' },
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.display_name && (
          <p id="initial-setup-display-name-error" className="auth-field__error">
            {errors.display_name.message}
          </p>
        )}
      </div>

      <div>
        <div className="auth-field">
          <label
            htmlFor="initial-setup-password"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            パスワード（8文字以上）
          </label>
          <input
            id="initial-setup-password"
            type="password"
            autoComplete="new-password"
            required
            className="auth-field__input"
            placeholder="パスワードを入力"
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'initial-setup-password-error' : undefined}
            {...register('password', {
              required: 'パスワードを入力してください',
              minLength: { value: 8, message: 'パスワードは8文字以上で入力してください' },
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.password && (
          <p id="initial-setup-password-error" className="auth-field__error">
            {errors.password.message}
          </p>
        )}
      </div>

      <div>
        <div className="auth-field">
          <label
            htmlFor="initial-setup-confirm-password"
            className="block text-xs font-medium text-[#8a8580] uppercase tracking-wider mb-1.5"
          >
            パスワード（確認）
          </label>
          {/* 確認欄は送らずここで照合する。最初の管理者はパスワードを誤ると、代わりに再設定できる
              管理者が他に居ない。 */}
          <input
            id="initial-setup-confirm-password"
            type="password"
            autoComplete="new-password"
            required
            className="auth-field__input"
            placeholder="もう一度入力"
            aria-invalid={!!errors.confirm_password}
            aria-describedby={
              errors.confirm_password ? 'initial-setup-confirm-password-error' : undefined
            }
            {...register('confirm_password', {
              required: '確認用のパスワードを入力してください',
              validate: (value, formValues) =>
                value === formValues.password || 'パスワードが一致しません',
            })}
          />
          <span className="auth-field__accent" />
        </div>
        {errors.confirm_password && (
          <p id="initial-setup-confirm-password-error" className="auth-field__error">
            {errors.confirm_password.message}
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
