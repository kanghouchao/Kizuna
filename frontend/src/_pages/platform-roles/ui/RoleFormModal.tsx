'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import {
  PermissionConsole,
  PermissionResponse,
  PlatformPermission,
  RoleResponse,
  platformRoleApi,
} from '@/entities/user';
import { getApiErrorMessage, isConflict, useManagedList } from '@/shared/lib';
import { Button, Dialog, DialogContent, DialogTitle, Input, Label } from '@/shared/ui';

interface RoleFormValues {
  name: string;
}

interface RoleFormModalProps {
  open: boolean;
  onClose: () => void;
  /** 編集対象。null なら新規作成。 */
  editing: RoleResponse | null;
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

/** ロールの新規作成・編集モーダル（名称 + 権限の複数選択）。 */
export function RoleFormModal({ open, onClose, editing, onSaved }: RoleFormModalProps) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<RoleFormValues>();
  const [permissions, setPermissions] = useState<PlatformPermission[]>([]);
  const { items: catalog, isLoading: catalogLoading } = useManagedList<PermissionResponse>(
    () => platformRoleApi.permissions(),
    '権限目録の取得に失敗しました'
  );

  useEffect(() => {
    if (!open) return;
    reset({ name: editing?.name ?? '' });
    setPermissions(editing?.permissions ?? []);
  }, [open, editing, reset]);

  const toggle = (code: PlatformPermission) => {
    setPermissions(current =>
      current.includes(code) ? current.filter(item => item !== code) : [...current, code]
    );
  };

  const submit = async (values: RoleFormValues) => {
    if (permissions.length === 0) {
      toast.error('権限を 1 つ以上選択してください');
      return;
    }
    try {
      if (editing) {
        await platformRoleApi.update(editing.id ?? 0, {
          name: values.name,
          permissions,
          // 楽観ロック用バージョン（応答の version をそのまま往復する）
          version: editing.version,
        });
        toast.success('ロールを更新しました');
      } else {
        await platformRoleApi.create({ name: values.name, permissions });
        toast.success('ロールを追加しました');
      }
      onSaved();
      onClose();
    } catch (error) {
      if (isConflict(error)) {
        // 楽観ロック競合。ここで再取得しないと editing が古い version を抱えたままになり、
        // 再試行はもちろん閉じて開き直しても同じ 409 を繰り返す（一覧から導出しているため）。
        // 一覧を取り直すと editing prop が入れ替わり useEffect が最新値で再初期化する。
        toast.error('他の管理者が更新しました。最新の内容を確認してください');
        onSaved();
      } else {
        toast.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
      }
    }
  };

  const title = editing ? `${editing.name} を編集` : 'ロールを追加';

  return (
    <Dialog
      open={open}
      onOpenChange={next => {
        if (!next) onClose();
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
        <form onSubmit={handleSubmit(submit)} className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="role-name">ロール名</Label>
            <Input
              id="role-name"
              type="text"
              maxLength={100}
              {...register('name', { required: true })}
            />
          </div>
          <div>
            <span className="mb-1 block text-sm font-medium text-foreground">権限</span>
            {catalogLoading ? (
              <p className="text-sm text-muted-foreground">読み込み中...</p>
            ) : (
              <div className="space-y-4 rounded-md border p-3">
                {groupByConsole(catalog).map(group => {
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
                                checked={permissions.includes(code)}
                                onChange={() => toggle(code)}
                              />
                              {code}
                            </Label>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
          <div className="flex justify-end gap-3 border-t pt-4">
            <Button type="button" variant="outline" onClick={onClose}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? '保存中...' : '保存する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
