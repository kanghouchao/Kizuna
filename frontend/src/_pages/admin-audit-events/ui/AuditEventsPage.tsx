'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { auditEventApi } from '@/entities/audit-event';
import { useCursorList, useResource } from '@/shared/lib';
import {
  Button,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  Input,
  Form,
  FormField,
  FormItem,
  FormControl,
  FormLabel,
  FormMessage,
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';

function AuditDetail({ id }: { id: number }) {
  const detail = useResource(() => auditEventApi.get(id), [id]);
  if (detail.isLoading) return <p>読み込み中...</p>;
  if (detail.failure || !detail.data)
    return (
      <RegionError
        message="監査の詳細を取得できませんでした"
        onRetry={() => void detail.reload()}
      />
    );
  const { event, before_values: before, after_values: after } = detail.data;
  const fields = [...new Set([...Object.keys(before), ...Object.keys(after)])].sort();
  return (
    <div className="space-y-6">
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <dt>操作主体</dt>
        <dd className="wrap-anywhere">
          {event.actor_name}（{event.actor_type} #{event.actor_id}）
        </dd>
        <dt>記録時刻</dt>
        <dd>{new Date(event.occurred_at).toLocaleString('ja-JP')}</dd>
        <dt>操作</dt>
        <dd className="wrap-anywhere">{event.action}</dd>
        <dt>結果</dt>
        <dd>{event.result}</dd>
        <dt>対象</dt>
        <dd className="wrap-anywhere">
          {event.target_type} #{event.target_id}
        </dd>
        <dt>店舗</dt>
        <dd>{event.store_id ? `#${event.store_id}` : '全体'}</dd>
        <dt>業務記録</dt>
        <dd className="wrap-anywhere">
          {event.source_type ? `${event.source_type} #${event.source_id}` : '—'}
        </dd>
      </dl>
      <TableCard>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>項目</TableHead>
              <TableHead>変更前</TableHead>
              <TableHead>変更後</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {fields.map(field => (
              <TableRow key={field}>
                <TableCell className="max-w-40 wrap-anywhere">{field}</TableCell>
                <TableCell className="max-w-48 whitespace-pre-wrap wrap-anywhere">
                  {before[field] ?? '—'}
                </TableCell>
                <TableCell className="max-w-48 whitespace-pre-wrap wrap-anywhere">
                  {after[field] ?? '—'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {fields.length === 0 && <p className="p-6 text-muted-foreground">変更摘要はありません</p>}
      </TableCard>
    </div>
  );
}

export default function AuditEventsPage() {
  const list = useCursorList((cursor, action: string) => auditEventApi.list(cursor, action), '');
  const search = useForm<{ action: string }>({ defaultValues: { action: '' } });
  const [selected, setSelected] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  return (
    <div className="space-y-6">
      <PageHeader
        title="監査履歴"
        description="操作主体・対象・変更前後の記録を確認します。記録はこの画面から変更できません。"
      />
      <Form {...search}>
        <form
          noValidate
          className="flex items-end gap-3"
          onSubmit={search.handleSubmit(values => list.search(values.action.trim()))}
        >
          <FormField
            control={search.control}
            name="action"
            rules={{
              validate: value =>
                !value.trim() ||
                /^[A-Z][A-Z0-9_]{0,79}$/.test(value.trim()) ||
                '半角大文字・数字・下線で操作コードを入力してください',
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>操作コード（完全一致）</FormLabel>
                <FormControl>
                  <Input placeholder="例: ROLE_CHANGED" maxLength={80} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={list.isLoading}>
            検索
          </Button>
          <Button
            variant="outline"
            type="button"
            disabled={list.isLoading}
            onClick={() => {
              search.reset();
              list.search('');
            }}
          >
            すべて表示
          </Button>
        </form>
      </Form>
      <TableCard>
        {list.isLoading ? (
          <p className="p-6">読み込み中...</p>
        ) : list.failed ? (
          <RegionError message="監査履歴を取得できませんでした" onRetry={list.reload} />
        ) : list.rows.length === 0 ? (
          <p className="p-6 text-muted-foreground">該当する監査履歴がありません</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>記録時刻</TableHead>
                <TableHead>操作主体</TableHead>
                <TableHead>操作</TableHead>
                <TableHead>対象</TableHead>
                <TableHead>詳細</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.rows.map(row => (
                <TableRow key={row.id}>
                  <TableCell>{new Date(row.occurred_at).toLocaleString('ja-JP')}</TableCell>
                  <TableCell className="max-w-56 wrap-anywhere">
                    {row.actor_name}
                    <span className="block text-muted-foreground">{row.actor_type}</span>
                  </TableCell>
                  <TableCell className="max-w-64 wrap-anywhere">{row.action}</TableCell>
                  <TableCell className="max-w-48 wrap-anywhere">
                    {row.target_type} #{row.target_id}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setSelected(row.id);
                        setDetailOpen(true);
                      }}
                    >
                      詳細
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {list.hasMore && (
          <div className="p-4 text-center">
            <Button variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
              さらに読み込む
            </Button>
          </div>
        )}
      </TableCard>
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>監査の詳細</DialogTitle>
            <DialogDescription>記録時点の主体と変更内容です。</DialogDescription>
          </DialogHeader>
          {selected !== null && <AuditDetail key={selected} id={selected} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}
