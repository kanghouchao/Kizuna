'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { auditEventApi } from '@/entities/audit-event';
import { useListPage, useResource } from '@/shared/lib';
import {
  Button,
  ChangeTable,
  Dialog,
  DialogFrame,
  Input,
  Form,
  FormField,
  FormItem,
  FormControl,
  FormLabel,
  FormMessage,
  KeyValueList,
  KeyValueRow,
  Pager,
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
      <KeyValueList>
        <KeyValueRow label="操作主体">
          {event.actor_name}（{event.actor_type} #{event.actor_id}）
        </KeyValueRow>
        <KeyValueRow label="記録時刻">
          {new Date(event.occurred_at).toLocaleString('ja-JP')}
        </KeyValueRow>
        <KeyValueRow label="操作">{event.action}</KeyValueRow>
        <KeyValueRow label="結果">{event.result}</KeyValueRow>
        <KeyValueRow label="対象">
          {event.target_type} #{event.target_id}
        </KeyValueRow>
        <KeyValueRow label="店舗">{event.store_id ? `#${event.store_id}` : '全体'}</KeyValueRow>
        <KeyValueRow label="業務記録">
          {event.source_type ? `${event.source_type} #${event.source_id}` : '—'}
        </KeyValueRow>
      </KeyValueList>
      <section className="space-y-2">
        <h3 className="font-medium">変更内容</h3>
        {fields.length === 0 ? (
          <p className="text-sm text-muted-foreground">変更摘要はありません</p>
        ) : (
          <ChangeTable
            rows={fields.map(field => ({
              label: field,
              before: before[field] ?? '—',
              after: after[field] ?? '—',
            }))}
          />
        )}
      </section>
    </div>
  );
}

export default function AuditEventsPage() {
  const list = useListPage((page, action: string) => auditEventApi.list(page, action), '');
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
          onSubmit={search.handleSubmit(values => void list.search(values.action.trim()))}
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
              void list.search('');
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
          <RegionError
            message="監査履歴を取得できませんでした"
            onRetry={() => void list.reload()}
          />
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
        <div className="p-4">
          <Pager
            page={list.page}
            pageCount={list.pageCount}
            total={list.total}
            rowCount={list.rows.length}
            disabled={list.isLoading}
            onPageChange={page => void list.onPageChange(page)}
          />
        </div>
      </TableCard>
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogFrame size="md" title="監査の詳細" description="記録時点の主体と変更内容です。">
          {selected !== null && <AuditDetail key={selected} id={selected} />}
        </DialogFrame>
      </Dialog>
    </div>
  );
}
