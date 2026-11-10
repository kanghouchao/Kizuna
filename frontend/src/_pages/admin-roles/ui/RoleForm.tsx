'use client';

import Link from 'next/link';
import { useId } from 'react';
import { useForm } from 'react-hook-form';
import { PermissionCode, PermissionTreeResponse, roleApi } from '@/entities/user';
import { useManagedList } from '@/shared/lib';
import {
  Button,
  Checkbox,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Label,
  RegionError,
} from '@/shared/ui';

export interface RoleFormValues {
  name: string;
  permissions: PermissionCode[];
}

interface RoleFormProps {
  /** 編集対象の現在値。省略は新規作成。到着後に mount する前提で、初期値としてだけ使う。 */
  initial?: RoleFormValues & { system: boolean };
  /** 保存処理。失敗の通知は呼び出し側が行い、送信中の状態はこの関数の完了まで保たれる。 */
  onSubmit: (values: RoleFormValues) => Promise<void>;
}

/** ロールの作成・編集に共通のフォーム（名称 + 「木 → 区分見出し」の下に並べた権限選択）。 */
export function RoleForm({ initial, onSubmit }: RoleFormProps) {
  const form = useForm<RoleFormValues>({
    defaultValues: { name: initial?.name ?? '', permissions: initial?.permissions ?? [] },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;
  const permissionsLabelId = useId();
  const {
    items: trees,
    isLoading: catalogLoading,
    failed: catalogFailed,
    refetch: refetchCatalog,
  } = useManagedList<PermissionTreeResponse>(() => roleApi.permissions());

  return (
    <Form {...form}>
      {/* handleSubmit の適用はイベント時まで遅らせる — 描画中の適用は react-hooks/refs が ref 読みとして拒む。
        noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
        我々の文言は永久に描かれない。執行は下の各 rules が担う */}
      <form
        onSubmit={event => void handleSubmit(onSubmit)(event)}
        noValidate
        className="max-w-2xl space-y-6"
      >
        <FormField
          control={control}
          name="name"
          rules={{
            required: 'ロール名を入力してください',
            maxLength: { value: 100, message: 'ロール名は 100 文字以内で入力してください' },
            validate: value => value.trim().length > 0 || 'ロール名を入力してください',
          }}
          render={({ field }) => (
            <FormItem className="gap-1">
              <FormLabel>ロール名</FormLabel>
              <FormControl>
                {/* 既定ロールの名称は播種の自然キーなので改名させない（権限だけ編集できる） */}
                <Input type="text" {...field} disabled={initial?.system === true} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={control}
          name="permissions"
          rules={{ validate: value => value.length > 0 || '権限を 1 つ以上選択してください' }}
          render={({ field }) => {
            // 焦点は先頭の権限が受ける
            const firstCode = trees
              .flatMap(tree => tree.groups.flatMap(group => group.permissions))
              .at(0)?.code;
            const toggle = (code: PermissionCode) => {
              field.onChange(
                field.value.includes(code)
                  ? field.value.filter(item => item !== code)
                  : [...field.value, code]
              );
            };
            return (
              <FormItem className="gap-1">
                <span id={permissionsLabelId} className="block text-sm font-medium text-foreground">
                  権限
                </span>
                <FormControl>
                  {/* 目録の到着前も組そのものは立てる。新規作成では目録の読み込み中でも
                    保存が押せるため、組が無いと指摘が欄と結び付かないまま出る */}
                  <div
                    role="group"
                    aria-labelledby={permissionsLabelId}
                    className="space-y-6 rounded-md border p-4"
                  >
                    {catalogLoading ? (
                      <p className="text-sm text-muted-foreground">読み込み中...</p>
                    ) : catalogFailed ? (
                      // 空の組は「権限が 1 つも無い」に見える。読めなかったことを名乗る
                      <RegionError
                        message="権限目録の取得に失敗しました"
                        onRetry={() => void refetchCatalog()}
                      />
                    ) : (
                      // 同じ見出し（業務 など）が両方の木に出るため、見出しは木の組の中で名乗る
                      trees.map(tree => (
                        <section
                          key={tree.tree}
                          role="group"
                          aria-labelledby={`${permissionsLabelId}-${tree.tree}`}
                          className="space-y-3"
                        >
                          <h2
                            id={`${permissionsLabelId}-${tree.tree}`}
                            className="border-b pb-1 text-base font-semibold text-foreground"
                          >
                            {tree.label}
                          </h2>
                          <div className="space-y-4 border-l-2 pl-3">
                            {tree.groups.map(group => (
                              <div key={group.group} className="space-y-1">
                                <h3 className="text-sm font-semibold text-muted-foreground">
                                  {group.label}
                                </h3>
                                <div className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
                                  {group.permissions.map(({ code, label }) => (
                                    <Label key={code} className="font-normal">
                                      <Checkbox
                                        checked={field.value.includes(code)}
                                        onCheckedChange={() => toggle(code)}
                                        ref={code === firstCode ? field.ref : undefined}
                                      />
                                      {label}
                                    </Label>
                                  ))}
                                </div>
                              </div>
                            ))}
                          </div>
                        </section>
                      ))
                    )}
                  </div>
                </FormControl>
                <FormDescription className="text-xs">1 つ以上を選択してください。</FormDescription>
                <FormMessage />
              </FormItem>
            );
          }}
        />
        <div className="flex justify-end gap-3 border-t pt-4">
          <Button variant="outline" render={<Link href="/admin/roles" />}>
            キャンセル
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '保存中...' : '保存する'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
