'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
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
  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<RoleFormValues>({ defaultValues: { name: '' } });
  const [permissions, setPermissions] = useState<PlatformPermission[]>([]);
  const [editingRole, setEditingRole] = useState<RoleResponse | null>(null);
  // 初回の詳細取得に失敗したときの再試行導線用。閉じて開き直す以外の回復手段を残す。
  const [detailLoadFailed, setDetailLoadFailed] = useState(false);
  const { items: catalog, isLoading: catalogLoading } = useManagedList<PermissionResponse>(
    () => platformRoleApi.permissions(),
    '権限目録の取得に失敗しました'
  );

  // 再試行の連打などで取得が並行しても、最新のリクエストだけがフォームを更新する
  const requestIdRef = useRef(0);

  // 編集対象の詳細取得。409 の後にも呼び、最新の name / permissions / version でフォームを
  // 初期化し直す（version 固着で再試行が同じ 409 を繰り返さないように）。取り直し中は
  // editingRole を空にして保存を止める — 残したままだと完了前の再送が陳腐な version で走る。
  const reloadEditingRole = useCallback(async () => {
    if (editingId === null) return;
    const requestId = ++requestIdRef.current;
    setEditingRole(null);
    setDetailLoadFailed(false);
    try {
      const role = await platformRoleApi.get(editingId);
      if (requestId !== requestIdRef.current) return;
      setEditingRole(role);
      reset({ name: role.name ?? '' });
      setPermissions(role.permissions ?? []);
    } catch (error) {
      if (requestId !== requestIdRef.current) return;
      toast.error(getApiErrorMessage(error, 'ロール情報の取得に失敗しました'));
      setDetailLoadFailed(true);
    }
  }, [editingId, reset]);

  useEffect(() => {
    void reloadEditingRole();
  }, [reloadEditingRole]);

  // 編集モードで詳細が未着のうちは保存させない（version 無しで送る事故を防ぐ）
  const editingLoading = editingId !== null && editingRole === null;

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
      if (editingId !== null) {
        if (editingRole === null) return;
        await platformRoleApi.update(editingId, {
          name: values.name,
          permissions,
          // 楽観ロック用バージョン（詳細応答の version をそのまま往復する）
          version: editingRole.version,
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
        // 楽観ロック競合。詳細を取り直さないと古い version を抱えたままになり、
        // 再試行が同じ 409 を繰り返す。一覧側も権限数・名称が変わりうるので再取得する。
        toast.error('他の管理者が更新しました。最新の内容を確認してください');
        onSaved();
        void reloadEditingRole();
      } else {
        toast.error(getApiErrorMessage(error, 'ロールの保存に失敗しました'));
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
        {/* submit は取り直し（requestIdRef を読む）へ繋がるため、handleSubmit の適用を
            イベント時まで遅らせる — 描画中の適用は react-hooks/refs が ref 読みとして拒む */}
        <form onSubmit={event => void handleSubmit(submit)(event)} className="space-y-4 px-6 py-5">
          <div className="grid gap-1">
            <Label htmlFor="role-name">ロール名</Label>
            <Input
              id="role-name"
              type="text"
              maxLength={100}
              // 詳細が届く前に入力させると、到着時の reset が入力を黙って上書きする
              disabled={editingLoading}
              {...register('name', { required: true })}
            />
          </div>
          <div>
            <span className="mb-1 block text-sm font-medium text-foreground">権限</span>
            {/* 詳細取得の失敗（初回・409 後の取り直しとも）は「読み込み中」に固着させず、
                ダイアログ内で再試行できるようにする */}
            {editingLoading && detailLoadFailed ? (
              <div className="space-y-2 rounded-md border p-3">
                <p className="text-sm text-destructive-strong">ロール情報の取得に失敗しました</p>
                <Button type="button" variant="outline" onClick={() => void reloadEditingRole()}>
                  再試行
                </Button>
              </div>
            ) : catalogLoading || editingLoading ? (
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
            <Button type="submit" disabled={isSubmitting || editingLoading}>
              {isSubmitting ? '保存中...' : '保存する'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
