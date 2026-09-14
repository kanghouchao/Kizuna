'use client';
import { useEffect, useState, useRef } from 'react';
import { useParams } from 'next/navigation';
import { serviceApi, ServiceSummary, ServiceKind, serviceKindLabels } from '@/entities/service';
import {
  getApiErrorMessage,
  hasPermission,
  isConflict,
  isForbidden,
  readTokenClaims,
  useListPage,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { ListPage } from '@/widgets/list-page';
import {
  Button,
  ConfirmDialog,
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from '@/shared/ui';
import { ServiceEditor } from './ServiceEditor';
import { ServiceHistory } from './ServiceHistory';

export default function ServicesPage() {
  const { storeId } = useParams<{ storeId: string }>();
  return <AuthorizedServices key={storeId} />;
}
function AuthorizedServices() {
  const [permitted, setPermitted] = useState<boolean | null>(null);
  useEffect(() => {
    setPermitted(hasPermission(readTokenClaims(), 'SERVICE_MANAGE'));
  }, []);
  if (permitted === null) return <p>読み込み中...</p>;
  if (!permitted)
    return <p role="alert">サービス設定の権限がありません。店長に確認してください。</p>;
  return <Services />;
}
function Services() {
  const [kind, setKind] = useState<ServiceKind | 'ALL'>('ALL');
  const [deleted, setDeleted] = useState(false);
  const [editor, setEditor] = useState<{ id: string | null } | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);
  const [target, setTarget] = useState<ServiceSummary | null>(null);
  const [removing, setRemoving] = useState(false);
  const [denied, setDenied] = useState(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const list = useListPage<ServiceSummary, { kind?: ServiceKind; deleted: boolean }>(
    async (page, criteria) => {
      try {
        return await serviceApi.list({ page, size: 20, ...criteria });
      } catch (error) {
        if (active.current && isForbidden(error)) setDenied(true);
        throw error;
      }
    },
    { deleted: false }
  );
  const remove = async (item: ServiceSummary) => {
    setRemoving(true);
    try {
      await serviceApi.remove(item.id, item.version);
      if (!active.current) return;
      notify.success('サービスを削除しました');
      await list.reload();
    } catch (error) {
      if (!active.current) return;
      if (isConflict(error)) {
        notify.warning('設定が変更されています。最新の内容を確認してから削除してください');
        await list.reload();
      } else if (isForbidden(error)) setDenied(true);
      else notify.error(getApiErrorMessage(error, 'サービスの削除に失敗しました'));
    } finally {
      if (active.current) setRemoving(false);
    }
  };
  const onForbidden = () => {
    if (active.current) setDenied(true);
  };
  if (denied) return <p role="alert">サービス設定の権限がありません。店長に確認してください。</p>;
  return (
    <>
      <ListPage
        title="サービス設定"
        description="店舗のコース・特殊サービス・加算と固定報酬を管理します。"
        actions={<Button onClick={() => setEditor({ id: null })}>新規作成</Button>}
        search={{
          onSearch: () => void list.search({ kind: kind === 'ALL' ? undefined : kind, deleted }),
          content: (
            <>
              <Select
                items={{ ALL: 'すべての種別', ...serviceKindLabels }}
                value={kind}
                onValueChange={value => {
                  if (value) setKind(value);
                }}
              >
                <SelectTrigger aria-label="種別で絞り込み">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">すべての種別</SelectItem>
                  {Object.entries(serviceKindLabels).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                items={{ active: '有効', deleted: '削除済み' }}
                value={deleted ? 'deleted' : 'active'}
                onValueChange={value => setDeleted(value === 'deleted')}
              >
                <SelectTrigger aria-label="状態で絞り込み">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="active">有効</SelectItem>
                  <SelectItem value="deleted">削除済み</SelectItem>
                </SelectContent>
              </Select>
              <Button type="submit" variant="outline">
                絞り込む
              </Button>
            </>
          ),
        }}
        state={list}
        onRetry={() => void list.reload()}
        errorMessage="サービス一覧の取得に失敗しました"
        emptyMessage="該当するサービスはありません"
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>種別</TableHead>
              <TableHead>所要時間</TableHead>
              <TableHead>価格</TableHead>
              <TableHead>固定報酬</TableHead>
              <TableHead>版本</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(item => (
              <TableRow key={item.id}>
                <TableCell className="max-w-64 break-all whitespace-normal">{item.name}</TableCell>
                <TableCell>
                  {serviceKindLabels[item.kind]}
                  {item.charge_type === 'FREE' && '（無料）'}
                </TableCell>
                <TableCell>
                  {item.duration_minutes === undefined ? '—' : `${item.duration_minutes} 分`}
                </TableCell>
                <TableCell>{item.price.toLocaleString()} 円</TableCell>
                <TableCell>{item.remuneration.toLocaleString()} 円</TableCell>
                <TableCell>{item.version}</TableCell>
                <TableCell>
                  <div className="flex gap-2">
                    {!item.deleted && (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setEditor({ id: item.id })}
                        >
                          編集
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setTarget(item)}
                          disabled={removing}
                        >
                          削除
                        </Button>
                      </>
                    )}
                    <Button variant="outline" size="sm" onClick={() => setHistoryId(item.id)}>
                      履歴
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      {editor && (
        <ServiceEditor
          key={editor.id ?? 'new'}
          id={editor.id}
          onClose={() => setEditor(null)}
          onSaved={() => void list.reload()}
          onForbidden={onForbidden}
        />
      )}
      {historyId && (
        <ServiceHistory
          key={historyId}
          id={historyId}
          onClose={() => setHistoryId(null)}
          onForbidden={onForbidden}
        />
      )}
      <ConfirmDialog
        open={!!target}
        title="サービスを削除しますか？"
        description={
          target
            ? `${target.name}（版本 ${target.version}）を新規候補から除外します。保存済みの条件と変更履歴は残ります。`
            : undefined
        }
        onClose={() => setTarget(null)}
        onConfirm={() => {
          if (target) void remove(target);
        }}
      />
    </>
  );
}
