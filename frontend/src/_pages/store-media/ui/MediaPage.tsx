'use client';

import Link from 'next/link';
import { ArrowLeftIcon, PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { mediumApi, type MediumSummary } from '@/entities/medium';
import { MePending, hasPermission, useMe } from '@/entities/user';
import { getApiErrorMessage, storePath, useDeleteAction, useManagedList } from '@/shared/lib';
import { notify } from '@/shared/notify';
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
import { ListPage } from '@/widgets/list-page';
import { MediumNameModal } from './MediumNameModal';

/** 広告費・受注・応募者が指す、店舗の媒体一覧の管理ページ。 */
export default function MediaPage() {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (!hasPermission(me, 'ADVERTISING_COST_VIEW'))
    return <p role="alert">媒体を閲覧する権限がありません。</p>;
  return <MediaList manage={hasPermission(me, 'ADVERTISING_COST_MANAGE')} />;
}

function MediaList({ manage }: { manage: boolean }) {
  const { items, isLoading, failed, refetch } = useManagedList<MediumSummary>(() =>
    mediumApi.list()
  );
  const [modalOpen, setModalOpen] = useState(false);
  const [renaming, setRenaming] = useState<MediumSummary | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const deletion = useDeleteAction<MediumSummary>({
    remove: medium => mediumApi.delete(medium.id, medium.version),
    successMessage: '媒体を削除しました',
    errorMessage: '媒体の削除に失敗しました',
    onDeleted: refetch,
  });

  const toggle = async (medium: MediumSummary) => {
    const stopping = medium.status === 'ACTIVE';
    setBusy(medium.id);
    try {
      if (stopping) await mediumApi.stop(medium.id, medium.version);
      else await mediumApi.resume(medium.id, medium.version);
      notify.success(stopping ? '媒体を停止しました' : '媒体を再開しました');
      void refetch();
    } catch (error) {
      notify.error(
        getApiErrorMessage(
          error,
          stopping ? '媒体の停止に失敗しました' : '媒体の再開に失敗しました'
        )
      );
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <Button
        variant="link"
        className="mb-2 px-0"
        render={<Link href={storePath('/advertising-costs')} />}
      >
        <ArrowLeftIcon />
        広告費へ戻る
      </Button>
      <ListPage
        title="媒体管理"
        description="広告費・受注・応募者で同じ媒体を指すための一覧です。停止した媒体は新しく選べなくなりますが、過去の記録には残ります。"
        actions={
          manage && (
            <Button
              onClick={() => {
                setRenaming(null);
                setModalOpen(true);
              }}
            >
              <PlusIcon />
              媒体を登録
            </Button>
          )
        }
        state={{ rows: items, isLoading, failed }}
        emptyMessage="媒体が登録されていません"
        errorMessage="媒体一覧の取得に失敗しました"
        onRetry={refetch}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>媒体名</TableHead>
              <TableHead>状態</TableHead>
              {manage && <TableHead className="text-right">操作</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map(medium => (
              <TableRow key={medium.id}>
                <TableCell className="max-w-md font-medium break-words whitespace-normal text-foreground">
                  {medium.name}
                </TableCell>
                <TableCell>
                  {medium.status === 'ACTIVE' ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      有効
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-muted text-foreground"
                    >
                      停止中
                    </Badge>
                  )}
                </TableCell>
                {manage && (
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="outline"
                        size="sm"
                        aria-label={`「${medium.name}」の名前を変更`}
                        onClick={() => {
                          setRenaming(medium);
                          setModalOpen(true);
                        }}
                      >
                        名前を変更
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={busy === medium.id}
                        aria-label={`「${medium.name}」を${medium.status === 'ACTIVE' ? '停止' : '再開'}`}
                        onClick={() => void toggle(medium)}
                      >
                        {medium.status === 'ACTIVE' ? '停止' : '再開'}
                      </Button>
                      <Button
                        variant="destructive"
                        size="sm"
                        aria-label={`「${medium.name}」を削除`}
                        onClick={() => deletion.ask(medium)}
                      >
                        削除
                      </Button>
                    </div>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      <MediumNameModal
        open={modalOpen}
        medium={renaming}
        onClose={() => setModalOpen(false)}
        onSaved={refetch}
      />
      <ConfirmDialog
        open={deletion.open}
        title={deletion.target ? `「${deletion.target.name}」を削除しますか？` : ''}
        description="広告費・受注・応募者のどこからも参照されていない媒体だけを削除できます。参照がある媒体は停止してください。"
        onConfirm={() => void deletion.confirm()}
        onClose={deletion.cancel}
      />
    </>
  );
}
