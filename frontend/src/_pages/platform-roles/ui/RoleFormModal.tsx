'use client';

import { useEffect, useId, useState } from 'react';
import { useForm } from 'react-hook-form';
import { notify } from '@/shared/notify';
import {
  PermissionConsole,
  PermissionResponse,
  PlatformPermission,
  RoleResponse,
  platformRoleApi,
} from '@/entities/user';
import { getApiErrorMessage, isConflict, useManagedList, useResource } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
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

interface RoleFormValues {
  name: string;
  permissions: PlatformPermission[];
}

interface RoleFormModalProps {
  onClose: () => void;
  /** 編集対象のロール id。null なら新規作成。 */
  editingId: number | null;
  /** 保存成功後に呼ばれる（一覧の再取得用）。 */
  onSaved: () => void;
}

// 見出しの訳語と並び順。目録に載る console はここから引くが、
// 表示する組は目録側から作る — この表に無い console が来ても権限を落とさないため
// （表で組を作ると、バックエンドが Console を増やした日にその権限が静かに消える）。
const CONSOLE_LABELS: Record<PermissionConsole, string> = {
  PLATFORM: 'プラットフォーム',
  STORE: '店舗',
  SHARED: '共通',
};
const CONSOLE_ORDER: PermissionConsole[] = ['PLATFORM', 'STORE', 'SHARED'];

/** 権限目録を console ごとの組へ畳む。未知の console は末尾に、コードそのままの見出しで残す。 */
function groupByConsole(
  catalog: PermissionResponse[]
): { key: string; label: string; items: PermissionResponse[] }[] {
  const buckets = new Map<string, PermissionResponse[]>();
  for (const entry of catalog) {
    const consoleKey = entry.console ?? '';
    const bucket = buckets.get(consoleKey);
    if (bucket) bucket.push(entry);
    else buckets.set(consoleKey, [entry]);
  }
  const rank = (key: string) => {
    const index = CONSOLE_ORDER.indexOf(key as PermissionConsole);
    return index === -1 ? CONSOLE_ORDER.length : index;
  };
  return [...buckets.entries()]
    .sort(([a], [b]) => rank(a) - rank(b))
    .map(([key, items]) => ({
      key,
      label: CONSOLE_LABELS[key as PermissionConsole] ?? key,
      items,
    }));
}

/**
 * ロールの新規作成・編集モーダル（名称 + 権限の複数選択）。
 * 開いたときだけ mount される前提。一覧は権限個数までの要約しか持たないため、編集フォームの
 * 中身（権限コードと楽観ロック用 version）は mount 時に id で個別取得する。権限目録の取得も
 * mount 時 = 開いた時点に遅延される。
 */
export function RoleFormModal({ onClose, editingId, onSaved }: RoleFormModalProps) {
  // 編集対象の詳細取得。409 の後にも取り直し、最新の name / permissions / version でフォームを
  // 初期化し直す（version 固着で再試行が同じ 409 を繰り返さないように）。
  const {
    data: editingRole,
    isLoading: detailLoading,
    failure: detailFailure,
    reload: reloadEditingRole,
  } = useResource(editingId === null ? null : () => platformRoleApi.get(editingId), [editingId]);

  const form = useForm<RoleFormValues>({ defaultValues: { name: '', permissions: [] } });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;
  const permissionsLabelId = useId();
  const {
    items: catalog,
    isLoading: catalogLoading,
    failed: catalogFailed,
    refetch: refetchCatalog,
  } = useManagedList<PermissionResponse>(() => platformRoleApi.permissions());
  // 届いた詳細をフォームへ移し終えたか。移した相手を持つのは、409 後の取り直しで届く別の
  // 詳細も同じ経路で載せ直すため
  const [seededRole, setSeededRole] = useState<RoleResponse | null>(null);

  useEffect(() => {
    if (editingRole !== null && editingRole !== seededRole) {
      reset({ name: editingRole.name ?? '', permissions: editingRole.permissions ?? [] });
      setSeededRole(editingRole);
    }
  }, [editingRole, seededRole, reset]);

  // 開いている間に他の管理者が消したロール。押しても永久に成功しない再試行を出さず、
  // 行き止まりであることを述べて閉じる操作を回復手段にする。
  const roleDeleted = detailFailure === 'notFound';
  useEffect(() => {
    // 消えたロールが一覧に残ったままだと、閉じた先で同じ行を開いて同じ行き止まりへ入る
    if (roleDeleted) onSaved();
  }, [roleDeleted, onSaved]);

  // 編集モードで詳細が未着のうちは保存させない（version 無しで送る事故を防ぐ）。取り直し中も
  // 同じ — 完了前の再送は陳腐な version で走る。詳細をフォームへ移すのは効果なので、移し
  // 終えるまでも「未着」に含める — 先に欄を出すと、その 1 回の描画が空の選択のまま残り
  // 「権限が 1 つも付いていない」に見える
  const editingLoading =
    editingId !== null && (detailLoading || editingRole === null || seededRole !== editingRole);

  const submit = async (values: RoleFormValues) => {
    try {
      if (editingId !== null) {
        if (editingRole === null) return;
        await platformRoleApi.update(editingId, {
          name: values.name,
          permissions: values.permissions,
          // 楽観ロック用バージョン（詳細応答の version をそのまま往復する）
          version: editingRole.version,
        });
        notify.success('ロールを更新しました');
      } else {
        await platformRoleApi.create({ name: values.name, permissions: values.permissions });
        notify.success('ロールを追加しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      if (isConflict(error)) {
        // 楽観ロック競合。詳細を取り直さないと古い version を抱えたままになり、
        // 再試行が同じ 409 を繰り返す。一覧側も権限数・名称が変わりうるので再取得する。
        notify.warning('他の管理者が更新しました。最新の内容を確認してください');
        onSaved();
        void reloadEditingRole();
      } else {
        notify.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
      }
    }
  };

  const title =
    editingId !== null
      ? editingRole !== null
        ? `${editingRole.name} を編集`
        : 'ロールを編集'
      : 'ロールを追加';

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
          {title}
        </DialogTitle>
        {roleDeleted ? (
          // 失敗はモーダルを開き終えた後に届くため、読み上げ利用者には焦点の移動が残っていない
          <div role="alert" className="space-y-4 px-6 py-5">
            <p className="text-sm text-destructive-strong">
              このロールは削除されました。一覧を最新の状態に戻しました。
            </p>
            <div className="flex justify-end border-t pt-4">
              <Button type="button" variant="outline" onClick={onClose}>
                閉じる
              </Button>
            </div>
          </div>
        ) : (
          <Form {...form}>
            {/* submit は取り直し（フックがリクエストカウンタを読む）へ繋がるため、handleSubmit の適用を
              イベント時まで遅らせる — 描画中の適用は react-hooks/refs が ref 読みとして拒む。
              noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
              我々の文言は永久に描かれない。執行は下の各 rules が担う */}
            <form
              onSubmit={event => void handleSubmit(submit)(event)}
              noValidate
              className="space-y-4 px-6 py-5"
            >
              <FormField
                control={control}
                name="name"
                rules={{ required: 'ロール名を入力してください' }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>ロール名</FormLabel>
                    <FormControl>
                      <Input
                        type="text"
                        maxLength={100}
                        {...field}
                        // 詳細が届く前に入力させると、到着時の reset が入力を黙って上書きする
                        disabled={editingLoading}
                      />
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
                  const groups = groupByConsole(catalog);
                  // 焦点は組の先頭が受ける
                  const firstCode = groups[0]?.items.find(entry => entry.code !== undefined)?.code;
                  const toggle = (code: PlatformPermission) => {
                    field.onChange(
                      field.value.includes(code)
                        ? field.value.filter(item => item !== code)
                        : [...field.value, code]
                    );
                  };
                  return (
                    <FormItem className="gap-1">
                      <span
                        id={permissionsLabelId}
                        className="block text-sm font-medium text-foreground"
                      >
                        権限
                      </span>
                      {/* 詳細取得の失敗（初回・409 後の取り直しとも）は「読み込み中」に固着させず、
                        ダイアログ内で再試行できるようにする。閉じて開き直す以外の回復手段を残す。
                        404 は上の行き止まりが引き取るので、ここへ来るのは再試行が効く失敗だけ */}
                      {editingLoading && detailFailure === 'error' ? (
                        <RegionError
                          message="ロール情報の取得に失敗しました"
                          onRetry={() => void reloadEditingRole()}
                          className="rounded-md border p-3"
                        />
                      ) : (
                        <FormControl>
                          {/* 目録の到着前も組そのものは立てる。新規作成では目録の読み込み中でも
                            保存が押せるため、組が無いと指摘が欄と結び付かないまま出る */}
                          <div
                            role="group"
                            aria-labelledby={permissionsLabelId}
                            className="space-y-4 rounded-md border p-3"
                          >
                            {catalogLoading || editingLoading ? (
                              // 詳細の到着前に選ばせると、到着時の reset が選択を黙って上書きする
                              <p className="text-sm text-muted-foreground">読み込み中...</p>
                            ) : catalogFailed ? (
                              // 空の組は「権限が 1 つも無い」に見える。読めなかったことを名乗る
                              <RegionError
                                message="権限目録の取得に失敗しました"
                                onRetry={() => void refetchCatalog()}
                              />
                            ) : (
                              groups.map(group => {
                                return (
                                  <div key={group.key}>
                                    <p className="mb-1 text-xs font-semibold tracking-widest text-muted-foreground uppercase">
                                      {group.label}
                                    </p>
                                    <div className="space-y-1">
                                      {group.items.map(entry => {
                                        const code = entry.code;
                                        if (code === undefined) return null;
                                        return (
                                          <Label key={code} className="font-normal">
                                            <input
                                              type="checkbox"
                                              checked={field.value.includes(code)}
                                              onChange={() => toggle(code)}
                                              ref={code === firstCode ? field.ref : undefined}
                                            />
                                            {code}
                                          </Label>
                                        );
                                      })}
                                    </div>
                                  </div>
                                );
                              })
                            )}
                          </div>
                        </FormControl>
                      )}
                      <FormDescription className="text-xs">
                        1 つ以上を選択してください。
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  );
                }}
              />
              <div className="flex justify-end gap-3 border-t pt-4">
                <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                  キャンセル
                </Button>
                <Button type="submit" disabled={isSubmitting || editingLoading}>
                  {isSubmitting ? '保存中...' : '保存する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
      </DialogContent>
    </Dialog>
  );
}
