'use client';

import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  PlatformStore,
  PlatformStoreScopeType,
  RoleSummaryResponse,
  platformRoleApi,
  platformStaffApi,
} from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
} from '@/shared/ui';
import { RolePicker } from './RolePicker';
import { StoreSetPicker } from './StoreSetPicker';

interface StaffCreateModalProps {
  /** 店舗目録（一覧ページが取得済みのものを共有する）。 */
  stores: PlatformStore[];
  storesLoading: boolean;
  /** 店舗目録の取り直し（取得失敗からの手動回復導線）。 */
  onReloadStores: () => void;
  onClose: () => void;
  /** 作成成功後に呼ばれる（一覧の再取得用）。 */
  onCreated: () => void;
}

/** 値はそのまま作成リクエストになるため、欄名は wire のキーに合わせる。 */
interface StaffCreateFormValues {
  email: string;
  password: string;
  display_name: string;
  role_ids: number[];
  store_scope_type: PlatformStoreScopeType;
  store_ids: number[];
}

// noValidate で type="email" の執行が止まるぶんを規則で引き継ぐ。ドメインに .
// を求める点で原生より厳しいが、これは既に StoreEditPage が採っている形。
const EMAIL_PATTERN = /^([^\s@])+@([^\s@])+\.[^\s@]+$/;

/**
 * スタッフの新規作成モーダル（メール・初期パスワード・氏名・ロール・担当店舗）。
 * 開いたときだけ mount される前提。ロール目録の取得は mount 時 = 開いた時点に遅延される。
 */
export function StaffCreateModal({
  stores,
  storesLoading,
  onReloadStores,
  onClose,
  onCreated,
}: StaffCreateModalProps) {
  const form = useForm<StaffCreateFormValues>({
    defaultValues: {
      email: '',
      password: '',
      display_name: '',
      role_ids: [],
      store_scope_type: 'ALL_STORES',
      store_ids: [],
    },
  });
  const {
    control,
    handleSubmit,
    setValue,
    formState: { isSubmitting },
  } = form;
  // 店舗集合は検証を持たないが、値は他の欄と同じ場所に置く（送信も初期化も一箇所で済む）
  const storeScopeType = useWatch({ control, name: 'store_scope_type' });
  const storeIds = useWatch({ control, name: 'store_ids' });
  const { items: roles, isLoading: rolesLoading } = useManagedList<RoleSummaryResponse>(
    () => platformRoleApi.list(),
    'ロール一覧の取得に失敗しました'
  );

  const submit = async (values: StaffCreateFormValues) => {
    try {
      await platformStaffApi.create(values);
      toast.success('スタッフを追加しました');
      onCreated();
      onClose();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'スタッフの追加に失敗しました'));
    }
  };

  return (
    <Dialog
      open
      onOpenChange={next => {
        // 送信中は閉じさせない。閉じると unmount で isSubmitting が消え、開き直した複製から
        // 二重送信できるうえ、古い継続の onClose が複製のモーダルまで閉じてしまう
        if (!next && !isSubmitting) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          スタッフを追加
        </DialogTitle>
        <Form {...form}>
          {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は下の各 rules が担う */}
          <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4 px-6 py-5">
            <FormField
              control={control}
              name="email"
              rules={{
                required: 'メールアドレスを入力してください',
                pattern: {
                  value: EMAIL_PATTERN,
                  message: 'メールアドレスの形式が正しくありません',
                },
              }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>メールアドレス</FormLabel>
                  <FormControl>
                    <Input type="email" maxLength={127} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="password"
              rules={{ required: '初期パスワードを入力してください' }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>初期パスワード</FormLabel>
                  <FormControl>
                    <Input type="password" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="display_name"
              rules={{ required: '氏名を入力してください' }}
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel>氏名</FormLabel>
                  <FormControl>
                    <Input type="text" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name="role_ids"
              rules={{
                validate: value => value.length > 0 || 'ロールを 1 つ以上選択してください',
              }}
              render={({ field }) => (
                <RolePicker
                  roles={roles}
                  isLoading={rolesLoading}
                  roleIds={field.value}
                  onChange={field.onChange}
                  ref={field.ref}
                />
              )}
            />
            <StoreSetPicker
              stores={stores}
              isLoading={storesLoading}
              onReload={onReloadStores}
              storeScopeType={storeScopeType}
              storeIds={storeIds}
              onChange={next => {
                setValue('store_scope_type', next.storeScopeType);
                setValue('store_ids', next.storeIds);
              }}
            />
            <div className="flex justify-end gap-3 border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                キャンセル
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? '追加中...' : '追加する'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
