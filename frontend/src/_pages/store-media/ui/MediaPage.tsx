'use client';

import Link from 'next/link';
import { ArrowLeftIcon, PlusIcon } from 'lucide-react';
import { useState } from 'react';
import { mediumApi, type MediumSummary } from '@/entities/medium';
import { MePending, hasPermission, useMe } from '@/entities/user';
import {
  storePath,
  useDeleteAction,
  useResource,
  useVersionedWrite,
  type WriteOutcome,
} from '@/shared/lib';
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
  const { data, setData, isLoading, failure, reload } = useResource<MediumSummary[]>(() =>
    mediumApi.list()
  );
  const items = data ?? [];
  // 更新の応答で行だけを差し替える。取り直すと表が読み込み中に外れ、押したボタンのフォーカスが失われる。
  const replace = (updated: MediumSummary) =>
    setData(rows => rows?.map(row => (row.id === updated.id ? updated : row)) ?? rows);
  const [modalOpen, setModalOpen] = useState(false);
  // 開いた時点の写し。版は送信時に一覧から引き直すので、競合後に取り直した版で送り直せる
  const [renaming, setRenaming] = useState<MediumSummary | null>(null);
  const renamingNow = renaming && items.find(row => row.id === renaming.id);
  const writer = useVersionedWrite(reload);
  // 一覧の行には失われる入力が無いので、409 は案内を出さずにその場で取り直す
  const resync = (outcome: WriteOutcome) => {
    if (outcome === 'conflict') void writer.reload();
    return outcome;
  };
  const deletion = useDeleteAction<MediumSummary>({
    remove: medium => mediumApi.delete(medium.id, medium.version),
    successMessage: '媒体を削除しました',
    errorMessage: '媒体の削除に失敗しました',
    reload,
  });

  const toggle = async (medium: MediumSummary) => {
    const stopping = medium.status === 'ACTIVE';
    resync(
      await writer.write(
        () =>
          stopping
            ? mediumApi.stop(medium.id, medium.version)
            : mediumApi.resume(medium.id, medium.version),
        {
          success: stopping ? '媒体を停止しました' : '媒体を再開しました',
          failure: stopping ? '媒体の停止に失敗しました' : '媒体の再開に失敗しました',
          onSuccess: replace,
        }
      )
    );
  };

  // 409 は名前の重複でも返るので、入力を残すためにダイアログは開いたままにする
  const submitName = async (name: string) => {
    let outcome: WriteOutcome = 'refused';
    if (renaming === null)
      outcome = await writer.write(() => mediumApi.create(name), {
        success: '媒体を登録しました',
        failure: '媒体の登録に失敗しました',
        onSuccess: () => void reload(),
      });
    else if (renamingNow)
      outcome = await writer.write(
        () => mediumApi.rename(renamingNow.id, name, renamingNow.version),
        {
          success: '媒体名を変更しました',
          failure: '媒体名の変更に失敗しました',
          onSuccess: replace,
        }
      );
    if (resync(outcome) === 'success') setModalOpen(false);
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
        state={{ rows: items, isLoading, failed: failure !== null }}
        emptyMessage="媒体が登録されていません"
        errorMessage="媒体一覧の取得に失敗しました"
        onRetry={() => void reload()}
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
                        aria-disabled={writer.busy}
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
        gone={renaming !== null && !isLoading && failure === null && !renamingNow}
        listFailed={renaming !== null && failure !== null}
        onRetryList={() => void reload()}
        busy={writer.busy}
        // 版は一覧の行から引くので、行が取れるまで（取得中・失敗）と競合後の取り直しが終わるまでは送らせない
        versionPending={writer.conflict || (renaming !== null && !renamingNow)}
        onClose={() => setModalOpen(false)}
        onSubmit={submitName}
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
