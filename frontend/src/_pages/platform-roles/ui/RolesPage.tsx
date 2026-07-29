'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { RoleResponse, platformRoleApi } from '@/entities/user';
import { getApiErrorMessage, useManagedList } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  ConfirmDialog,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { RoleFormModal } from './RoleFormModal';

/** ロール一覧ページ。一覧内モーダルで新規作成・編集を行う。 */
export default function RolesPage() {
  const {
    items: roles,
    isLoading,
    refetch,
  } = useManagedList<RoleResponse>(() => platformRoleApi.list(), 'ロール一覧の取得に失敗しました');
  // フォームは 1 つだけ開く。新規と編集で実体を分けると権限目録の取得が二重に走るため、
  // 「閉じている / 新規 / 既存の編集」を 1 つの状態で表す。
  // 編集対象は id で保持し、role オブジェクトは現在の一覧から導出する。
  // これにより refetch がそのままモーダル内容の最新化になる（409 リフレッシュ）。
  const [formTarget, setFormTarget] = useState<'closed' | 'create' | number>('closed');
  const editingRole =
    typeof formTarget === 'number' ? (roles.find(role => role.id === formTarget) ?? null) : null;
  const formOpen = formTarget === 'create' || editingRole !== null;
  const [deleteTarget, setDeleteTarget] = useState<RoleResponse | null>(null);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await platformRoleApi.remove(deleteTarget.id);
      toast.success('ロールを削除しました');
      void refetch();
    } catch (error) {
      // 授与中のロール削除は 409、平台既定ロールは 400。文言はサーバ側が持つ。
      toast.error(getApiErrorMessage(error, 'ロールの削除に失敗しました'));
    }
  };

  return (
    <>
      <ListPage
        title="ロール管理"
        // 権限の実効はログイン時に鋳造される JWT が根拠のため、変更は次回ログインまで反映されない。
        // 付与操作の直後に画面が変わらない理由になるので、一覧の手前で断っておく。
        description="ロールと、それが束ねる権限を定義します。スタッフへの付与はスタッフ管理から行います。権限の変更は、対象スタッフが次にログインしたときからメニューと画面に反映されます。"
        actions={
          <Button onClick={() => setFormTarget('create')}>
            <PlusIcon />
            ロールを追加
          </Button>
        }
        state={{ rows: roles, isLoading }}
        emptyMessage="ロールが登録されていません"
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ロール名</TableHead>
              <TableHead>権限数</TableHead>
              <TableHead>種別</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {roles.map(role => (
              <TableRow key={role.id}>
                <TableCell className="font-medium text-foreground">{role.name}</TableCell>
                <TableCell className="text-muted-foreground">
                  {role.permissions.length} 件
                </TableCell>
                <TableCell>
                  {role.system ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-muted text-muted-foreground"
                    >
                      既定
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      カスタム
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="space-x-2 text-right">
                  {/* 平台既定ロールは改名・権限変更・削除がサーバ側で拒否されるため導線自体を無効化する */}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    disabled={role.system}
                    onClick={() => setFormTarget(role.id)}
                  >
                    編集
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive-strong"
                    disabled={role.system}
                    onClick={() => setDeleteTarget(role)}
                  >
                    削除
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <RoleFormModal
        open={formOpen}
        editing={editingRole}
        onClose={() => setFormTarget('closed')}
        onSaved={refetch}
      />
      <ConfirmDialog
        open={deleteTarget !== null}
        title={`${deleteTarget?.name ?? ''} を削除しますか？`}
        description="このロールを付与されているスタッフがいる場合は削除できません。"
        onConfirm={handleDelete}
        onClose={() => setDeleteTarget(null)}
      />
    </>
  );
}
