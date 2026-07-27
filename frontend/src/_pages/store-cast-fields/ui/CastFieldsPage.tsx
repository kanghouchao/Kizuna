'use client';

import { PencilSquareIcon, PlusIcon, TrashIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';
import { CastFieldDefinitionResponse, castFieldDefinitionApi } from '@/entities/cast';
import { useManagedList } from '@/shared/lib';
import { PageHeader } from '@/widgets/page-header';
import {
  Badge,
  Button,
  Card,
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

  const handleDelete = async (id: string, label: string) => {
    if (!confirm(`「${label}」を削除しますか？`)) return;
    try {
      await castFieldDefinitionApi.delete(id);
      toast.success('フィールドを削除しました');
      void refetch();
    } catch {
      toast.error('フィールドの削除に失敗しました');
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="カスタムフィールド管理"
        description="キャストの追加プロフィール項目を定義します。公開設定した項目は公開詳細ページに表示されます。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            フィールドを追加
          </Button>
        }
      />

      <Card className="py-0 overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : definitions.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">
            フィールドが登録されていません
          </div>
        ) : (
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
                <TableRow
                  key={definition.id}
                  className="cursor-pointer"
                  onClick={() => setEditing(definition)}
                >
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
                  <TableCell className="text-muted-foreground">
                    {definition.display_order}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={e => {
                          e.stopPropagation();
                          setEditing(definition);
                        }}
                      >
                        <PencilSquareIcon />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={e => {
                          e.stopPropagation();
                          void handleDelete(definition.id, definition.label);
                        }}
                        aria-label="削除"
                      >
                        <TrashIcon />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

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
    </div>
  );
}
