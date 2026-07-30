'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { RoleResponse, platformRoleApi } from '@/entities/user';
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
import { RoleFormModal } from './RoleFormModal';

/** ロール一覧ページ。一覧内モーダルで新規作成・編集を行う。 */
export default function RolesPage() {
  const {
    items: allRoles,
    isLoading,
    refetch,
  } = useManagedList<RoleResponse>(() => platformRoleApi.list(), 'ロール一覧の取得に失敗しました');
  const [searchTerm, setSearchTerm] = useState('');
  // ロールは定義数が限られ、GET /platform/roles も全量を返す（付与 UI のロール目録が同じ端点を
  // 使うため分頁できない）。絞り込みは取得済みの配列に対して行い、送信で確定させる。
  const [appliedSearch, setAppliedSearch] = useState('');
  const roles = appliedSearch
    ? allRoles.filter(role => (role.name ?? '').toLowerCase().includes(appliedSearch.toLowerCase()))
    : allRoles;
  // フォームは 1 つだけ開く。新規と編集で実体を分けると権限目録の取得が二重に走るため、
  // 「閉じている / 新規 / 既存の編集」を 1 つの状態で表す。
  // 編集対象は id で保持し、role オブジェクトは現在の一覧から導出する。
  // これにより refetch がそのままモーダル内容の最新化になる（409 リフレッシュ）。
  // 導出元は絞り込み前の allRoles。roles から引くと、409 の再取得で他の管理者による改名が
  // 入った瞬間に絞り込みから外れ、最新値を見せるはずのモーダルが黙って閉じてしまう。
  const [formTarget, setFormTarget] = useState<'closed' | 'create' | number>('closed');
  const editingRole =
    typeof formTarget === 'number' ? (allRoles.find(role => role.id === formTarget) ?? null) : null;
  const formOpen = formTarget === 'create' || editingRole !== null;
  // 授与中のロール削除は 409、平台既定ロールは 400。文言はサーバ側が持つ。
  const deletion = useDeleteAction<RoleResponse>({
    remove: role => platformRoleApi.remove(role.id ?? 0),
    successMessage: 'ロールを削除しました',
    errorMessage: 'ロールの削除に失敗しました',
    onDeleted: refetch,
  });

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
        state={{ rows: roles, isLoading }}
        emptyMessage={
          appliedSearch ? '該当するロールが見つかりません' : 'ロールが登録されていません'
        }
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
                  {role.permissions?.length ?? 0} 件
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
                    onClick={() => setFormTarget(role.id ?? 0)}
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

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <RoleFormModal
        open={formOpen}
        editing={editingRole}
        onClose={() => setFormTarget('closed')}
        onSaved={refetch}
      />
      <ConfirmDialog
        open={deletion.target !== null}
        title={`${deletion.target?.name ?? ''} を削除しますか？`}
        description="このロールを付与されているスタッフがいる場合は削除できません。"
        onConfirm={() => void deletion.confirm()}
        onClose={deletion.cancel}
      />
    </>
  );
}
