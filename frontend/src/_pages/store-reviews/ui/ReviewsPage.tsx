'use client';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useParams } from 'next/navigation';
import { Review, ReviewCommand, ReviewSearch, reviewApi } from '@/entities/review';
import {
  readTokenClaims,
  hasPermission,
  useListPage,
  getApiErrorMessage,
  isBadRequest,
  isConflict,
  isNotFound,
} from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { statuses, permissions, time } from './labels';
import { ReviewFilters } from './ReviewFilters';
import { ReviewDetail } from './ReviewDetail';
import { Editor, ReviewEditor, editorTitle } from './ReviewEditor';
import { accessError } from './reviewErrors';
import { notify } from '@/shared/notify';
const subscribe = () => () => {};
export function ReviewsPage() {
  const mounted = useSyncExternalStore(
    subscribe,
    () => true,
    () => false
  );
  const { storeId } = useParams<{ storeId: string }>();
  if (!mounted) return <p>読み込み中...</p>;
  const claims = readTokenClaims();
  if (claims?.userType !== 'STAFF' || !hasPermission(claims, 'REVIEW_VIEW'))
    return <div role="alert">口コミの閲覧権限がありません</div>;
  return (
    <Reviews
      key={storeId}
      canModerate={hasPermission(claims, 'REVIEW_MODERATE')}
      canManage={hasPermission(claims, 'REVIEW_MANAGE')}
      canOrder={hasPermission(claims, 'ORDER_MANAGE')}
    />
  );
}
function Reviews({
  canModerate,
  canManage,
  canOrder,
}: {
  canModerate: boolean;
  canManage: boolean;
  canOrder: boolean;
}) {
  const [filters, setFilters] = useState<ReviewSearch>({});
  const [filterError, setFilterError] = useState<string | null>(null);
  const list = useListPage((page, criteria: ReviewSearch) => reviewApi.list(page, criteria), {});
  const search = () => {
    if (
      (filters.q?.trim().length ?? 0) > 60 ||
      (filters.review_id && !/^[0-9]{1,32}$/.test(filters.review_id))
    ) {
      setFilterError('表示名は60文字以内、口コミIDは1〜32桁の数字で指定してください');
      return;
    }
    setFilterError(null);
    void list.search({
      ...filters,
      q: filters.q?.trim() || undefined,
      review_id: filters.review_id || undefined,
    });
  };
  const [selected, setSelected] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [latest, setLatest] = useState<Review | undefined>();
  const [pending, setPending] = useState<ReviewCommand | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [denied, setDenied] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [revision, setRevision] = useState(0);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const submit = async (command: ReviewCommand) => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setPending(command);
    try {
      const result = await reviewApi.write(command);
      if (!active.current) return;
      setPending(null);
      setUncertain(false);
      setDenied(null);
      setConflict(false);
      setMissing(false);
      setLatest(result.review);
      setSelected(result.review.id);
      setOpen(true);
      setEditOpen(false);
      void list.reload();
      notify.success('口コミの操作を記録しました');
    } catch (error) {
      if (!active.current) return;
      if (
        isBadRequest(error) ||
        isConflict(error) ||
        isNotFound(error) ||
        accessError(error) !== null
      ) {
        if (!uncertain || isBadRequest(error) || isConflict(error) || isNotFound(error)) {
          setPending(null);
          setUncertain(false);
        }
        if (isConflict(error)) {
          setConflict(true);
          setEditOpen(false);
          setLatest(undefined);
        }
        if (isNotFound(error)) {
          setMissing(true);
          setEditOpen(false);
          void list.reload();
        }
        if (accessError(error) !== null) {
          setDenied(accessError(error));
          setOpen(false);
          void list.reload();
          setEditOpen(false);
        }
      } else setUncertain(true);
      notify.error(getApiErrorMessage(error, '口コミの操作結果が確認できません'));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  const feedback = (
    <>
      {pending && uncertain && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>結果が確認できません。同じ要求で結果を確認するまで、新しい操作はできません。</p>
          <Button variant="outline" disabled={busy} onClick={() => void submit(pending)}>
            同じ要求で結果を確認
          </Button>
        </div>
      )}
      {denied && <div role="alert">{denied}。権限とログイン状態を確認してください。</div>}
      {conflict && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>口コミが更新されています。最新状態を確認してから、改めて操作を選んでください。</p>
          <Button
            variant="outline"
            onClick={() => {
              setLatest(undefined);
              setConflict(false);
              setRevision(value => value + 1);
              void list.reload();
            }}
          >
            最新状態を確認して戻る
          </Button>
        </div>
      )}
    </>
  );
  return (
    <>
      {!open && !editOpen && feedback}
      <ListPage
        title="口コミ管理"
        description="スタッフが受け付けた口コミを審査し、公開許可を別途記録します。"
        actions={
          canManage && (
            <Button
              disabled={busy || pending !== null || denied !== null}
              onClick={() => {
                setEditor({ action: 'CREATE' });
                setEditOpen(true);
              }}
            >
              口コミを受付
            </Button>
          )
        }
        search={{
          content: <ReviewFilters value={filters} onChange={setFilters} error={filterError} />,
          onSearch: search,
        }}
        state={{ ...list }}
        emptyMessage="口コミはありません"
        errorMessage={
          accessError(list.error)?.replace('操作', '閲覧') ?? '口コミ一覧を取得できませんでした'
        }
        onRetry={() => void list.reload()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>受付・表示名</TableHead>
              <TableHead>審査</TableHead>
              <TableHead>公開許可</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(r => (
              <TableRow key={r.id}>
                <TableCell>
                  <p className="wrap-anywhere">{r.display_name ?? '匿名'}</p>
                  <p>{time(r.received_at)}</p>
                  <p className="text-sm text-muted-foreground">スタッフによる記録</p>
                </TableCell>
                <TableCell>{statuses[r.status]}</TableCell>
                <TableCell>{permissions[r.permission_status]}</TableCell>
                <TableCell>
                  <Button
                    variant="outline"
                    onClick={() => {
                      setLatest(undefined);
                      setSelected(r.id);
                      setMissing(false);
                      setConflict(false);
                      setOpen(true);
                    }}
                  >
                    内容・履歴
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>口コミの内容・履歴</DialogTitle>
            <DialogDescription>受付本文と判断、公開許可の記録を確認します。</DialogDescription>
          </DialogHeader>
          {!editOpen && feedback}
          {missing ? (
            <div role="alert">
              <p>口コミが見つかりません</p>
              <Button
                onClick={() => {
                  setOpen(false);
                  setMissing(false);
                }}
              >
                一覧へ戻る
              </Button>
            </div>
          ) : (
            selected && (
              <ReviewDetail
                key={`${selected}-${revision}`}
                id={selected}
                initial={latest?.id === selected ? latest : undefined}
                canModerate={canModerate && !denied && !conflict}
                canManage={canManage && !denied && !conflict}
                busy={busy || pending !== null}
                onEdit={value => {
                  setEditor(value);
                  setEditOpen(true);
                }}
                onMissing={() => {
                  setOpen(false);
                  void list.reload();
                }}
              />
            )
          )}
        </DialogContent>
      </Dialog>
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editor ? editorTitle(editor) : '口コミの操作'}</DialogTitle>
            <DialogDescription>理由と対象を確認して記録してください。</DialogDescription>
          </DialogHeader>
          {pending && uncertain ? (
            <p role="alert">
              結果が確認できません。閉じて「同じ要求で結果を確認」から再確認できます。
            </p>
          ) : (
            editor && (
              <ReviewEditor
                key={`${editor.action}-${editor.row?.id ?? 'new'}-${editor.row?.version ?? 0}`}
                editor={editor}
                canOrder={canOrder}
                busy={busy || pending !== null}
                onSubmit={submit}
              />
            )
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
