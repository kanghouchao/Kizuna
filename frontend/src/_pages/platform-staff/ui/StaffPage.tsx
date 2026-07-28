'use client';

import { PlusIcon } from 'lucide-react';
import { useState } from 'react';
import {
  PlatformStaffResponse,
  PlatformStore,
  platformAuthApi,
  platformStaffApi,
} from '@/entities/user';
import {
  StaffCreateModal,
  StaffEditDrawer,
  bundleSetLabel,
  storeSetLabel,
} from '@/features/staff-management';
import { useManagedList } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** スタッフ一覧ページ。一覧内モーダル=新規作成、ドロワー=編集。 */
export default function StaffPage() {
  const {
    items: staff,
    isLoading,
    refetch,
  } = useManagedList<PlatformStaffResponse>(
    () => platformStaffApi.list(),
    'スタッフ一覧の取得に失敗しました'
  );
  const { items: stores } = useManagedList<PlatformStore>(
    () => platformAuthApi.stores(),
    '店舗一覧の取得に失敗しました'
  );
  const [createOpen, setCreateOpen] = useState(false);
  // 編集対象は id で保持し、staff オブジェクトは現在の一覧から導出する。
  // これにより refetch がそのままドロワー内容の最新化になる（409 リフレッシュ）。
  const [editingId, setEditingId] = useState<number | null>(null);
  const editingStaff = staff.find(member => member.id === editingId) ?? null;

  return (
    <>
      <ListPage
        title="スタッフ管理"
        description="権限束・担当店舗・精算範囲の付与と編集ができます。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon />
            スタッフを追加
          </Button>
        }
        state={{ rows: staff, isLoading }}
        emptyMessage="スタッフが登録されていません"
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>氏名</TableHead>
              <TableHead>権限束</TableHead>
              <TableHead>状態</TableHead>
              <TableHead>担当店舗</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {staff.map(member => (
              <TableRow
                key={member.id}
                className="cursor-pointer"
                onClick={() => setEditingId(member.id)}
              >
                <TableCell className="font-medium text-foreground">{member.display_name}</TableCell>
                <TableCell className="text-muted-foreground">
                  {bundleSetLabel(member.bundles)}
                </TableCell>
                <TableCell>
                  {member.enabled ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      有効
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-warning/10 text-warning-strong"
                    >
                      停止中
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {storeSetLabel(member.store_scope_type, member.store_ids, stores)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-primary-strong"
                    onClick={e => {
                      e.stopPropagation();
                      setEditingId(member.id);
                    }}
                  >
                    編集
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {/* モーダル・ドロワーは一覧の loading / empty に連動して消えないよう外殻の外に置く */}
      <StaffCreateModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={refetch}
      />
      <StaffEditDrawer
        open={editingId !== null}
        staff={editingStaff}
        onClose={() => setEditingId(null)}
        onUpdated={refetch}
      />
    </>
  );
}
