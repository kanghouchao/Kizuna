'use client';

import { PlusIcon } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';
import { RoleSummaryResponse, roleApi } from '@/entities/user';
import { useDeleteAction, useManagedList } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  ConfirmDialog,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** ロール一覧ページ。新規作成・編集は独立した画面（/admin/roles/create、/admin/roles/{id}/edit）で行う。 */
export default function RolesPage() {
  const {
    items: allRoles,
    isLoading,
    failed,
    refetch,
  } = useManagedList<RoleSummaryResponse>(() => roleApi.list());
  const [searchTerm, setSearchTerm] = useState('');
  // ロールは定義数が限られ、GET /roles も全量を返す（付与 UI のロール目録が同じ端点を
  // 使うため分頁できない）。絞り込みは取得済みの配列に対して行い、送信で確定させる。
  const [appliedSearch, setAppliedSearch] = useState('');
  const roles = appliedSearch
    ? allRoles.filter(role => (role.name ?? '').toLowerCase().includes(appliedSearch.toLowerCase()))
    : allRoles;
  // 授与中のロール削除は 409、既定ロールは 400。文言はサーバ側が持つ。
  const deletion = useDeleteAction<RoleSummaryResponse>({
    remove: role => roleApi.remove(role.id ?? 0),
    successMessage: 'ロールを削除しました',
    errorMessage: 'ロールの削除に失敗しました',
    onDeleted: refetch,
  });

  return (
    <>
      <ListPage
        title="ロール管理"
        // 権限の変更は保持者全員のセッションを失効させる（自分も含む）ので、編集の前に断っておく。
        description="ロールと、それが束ねる権限を定義します。アカウントへの付与は従業員管理から行います。権限を変更すると、そのロールを持つアカウントは（自分を含め）再ログインが必要になります。"
        actions={
          <Button render={<Link href="/admin/roles/create" />}>
            <PlusIcon />
            ロールを追加
          </Button>
        }
        search={{
          onSearch: () => setAppliedSearch(searchTerm),
          content: (
            <>
              <div className="w-full md:max-w-xs">
                <label htmlFor="search" className="sr-only">
                  ロールを検索
                </label>
                <Input
                  type="text"
                  name="search"
                  id="search"
                  value={searchTerm}
                  onChange={e => setSearchTerm(e.target.value)}
                  placeholder="ロール名で検索..."
                />
              </div>
              <Button type="submit">検索</Button>
              {searchTerm && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSearchTerm('');
                    setAppliedSearch('');
                  }}
                >
                  クリア
                </Button>
              )}
            </>
          ),
        }}
        state={{ rows: roles, isLoading, failed }}
        emptyMessage={
          appliedSearch ? '該当するロールが見つかりません' : 'ロールが登録されていません'
        }
        errorMessage="ロール一覧の取得に失敗しました"
        onRetry={refetch}
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
                <TableCell className="text-muted-foreground">{role.permission_count} 件</TableCell>
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
                  {/* 既定ロールは権限を編集できる（改名は編集画面が塞ぎ、削除はサーバ側で拒否されるため導線を無効化する） */}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    // id が無ければ編集対象を特定できないため、リンクにせず無効化する
                    disabled={role.id === undefined}
                    render={
                      role.id !== undefined ? (
                        <Link href={`/admin/roles/${role.id}/edit`} />
                      ) : undefined
                    }
                  >
                    編集
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive-strong"
                    disabled={role.system}
                    onClick={() => deletion.ask(role)}
                  >
                    削除
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      <ConfirmDialog
        open={deletion.open}
        title={`${deletion.target?.name ?? ''} を削除しますか？`}
        description="このロールを付与されているスタッフがいる場合は削除できません。"
        onConfirm={() => void deletion.confirm()}
        onClose={deletion.cancel}
      />
    </>
  );
}
