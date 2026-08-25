'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { RoleSummaryResponse, platformRoleApi } from '@/entities/user';
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
    failed,
    refetch,
  } = useManagedList<RoleSummaryResponse>(() => platformRoleApi.list());
  const [searchTerm, setSearchTerm] = useState('');
  // ロールは定義数が限られ、GET /platform/roles も全量を返す（付与 UI のロール目録が同じ端点を
  // 使うため分頁できない）。絞り込みは取得済みの配列に対して行い、送信で確定させる。
  const [appliedSearch, setAppliedSearch] = useState('');
  const roles = appliedSearch
    ? allRoles.filter(role => (role.name ?? '').toLowerCase().includes(appliedSearch.toLowerCase()))
    : allRoles;
  // フォームは 1 つだけ開く（閉じている / 新規 / 既存 id の編集）。一覧は権限個数までの要約しか
  // 持たないため、編集フォームの中身（権限コードと version）はモーダル側が id で個別取得する。
  const [formTarget, setFormTarget] = useState<'closed' | 'create' | number>('closed');
  // 授与中のロール削除は 409、平台既定ロールは 400。文言はサーバ側が持つ。
  const deletion = useDeleteAction<RoleSummaryResponse>({
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
        description="ロールと、それが束ねる権限を定義します。アカウントへの付与は管理者管理から行います。権限の変更は、対象アカウントが次にログインしたときからメニューと画面に反映されます。"
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
                  {/* 平台既定ロールは改名・権限変更・削除がサーバ側で拒否されるため導線自体を無効化する */}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    // id が無ければ編集対象を特定できず、押しても何も起きない状態になるため無効化する
                    disabled={role.system || role.id === undefined}
                    onClick={() => role.id !== undefined && setFormTarget(role.id)}
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

      {/* モーダルは一覧の loading / empty に連動して消えないよう外殻の外に置く。
          開くまで mount しないことで、権限目録とロール詳細の取得を必要になった時点まで遅延させる */}
      {formTarget !== 'closed' && (
        <RoleFormModal
          editingId={typeof formTarget === 'number' ? formTarget : null}
          onClose={() => setFormTarget('closed')}
          onSaved={refetch}
        />
      )}
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
