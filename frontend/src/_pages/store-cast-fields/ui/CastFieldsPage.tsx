'use client';

import { PlusIcon, SquarePenIcon, Trash2Icon } from 'lucide-react';
import { useState } from 'react';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';
import { useManagedList } from '@/shared/lib';
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
import { toast } from 'react-hot-toast';
import { CastFieldCreateModal } from './CastFieldCreateModal';
import { CastFieldEditModal } from './CastFieldEditModal';

/** キャストのカスタムフィールド定義管理ページ。 */
export default function CastFieldsPage() {
  const {
    items: definitions,
    isLoading,
    refetch,
  } = useManagedList<CastFieldDefinitionResponse>(
    () => castFieldDefinitionApi.list(),
    'フィールド定義一覧の取得に失敗しました'
  );
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<CastFieldDefinitionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CastFieldDefinitionResponse | null>(null);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await castFieldDefinitionApi.delete(deleteTarget.id);
      toast.success('フィールドを削除しました');
      void refetch();
    } catch {
      toast.error('フィールドの削除に失敗しました');
    }
  };

  return (
    <>
      <ListPage
        title="カスタムフィールド管理"
        description="キャストの追加プロフィール項目を定義します。公開設定した項目は公開詳細ページに表示されます。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            フィールドを追加
          </Button>
        }
        state={{ rows: definitions, isLoading }}
        emptyMessage="フィールドが登録されていません"
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>key</TableHead>
              <TableHead>label</TableHead>
              <TableHead>公開設定</TableHead>
              <TableHead>表示順</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {definitions.map(definition => (
              <TableRow key={definition.id}>
                <TableCell className="font-medium text-foreground">{definition.key}</TableCell>
                <TableCell className="text-muted-foreground">{definition.label}</TableCell>
                <TableCell>
                  {definition.is_public ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      公開
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-muted text-foreground"
                    >
                      非公開
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">{definition.display_order}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="編集"
                      onClick={() => setEditing(definition)}
                    >
                      <SquarePenIcon />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="削除"
                      onClick={() => setDeleteTarget(definition)}
                    >
                      <Trash2Icon />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダル・ダイアログは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <CastFieldCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={refetch}
      />
      <CastFieldEditModal
        open={editing !== null}
        definition={editing}
        onClose={() => setEditing(null)}
        onUpdated={refetch}
      />
      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `「${deleteTarget.label}」を削除しますか？` : ''}
        onConfirm={() => void handleDelete()}
        onClose={() => setDeleteTarget(null)}
      />
    </>
  );
}
