'use client';

import { useState } from 'react';
import Link from 'next/link';
import Cookies from 'js-cookie';
import { useForm } from 'react-hook-form';
import {
  EmergencyElevationActivationResponse,
  EmergencyElevationSummary,
  PlatformStore,
  emergencyElevationApi,
  platformAuthApi,
} from '@/entities/user';
import { getApiErrorMessage, storeEntryPath, useCursorList, useManagedList } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
} from '@/shared/ui';
import { PageHeader } from '@/widgets/page-header';

interface ActivationFormValues {
  store_id: string;
  reason: string;
  password: string;
}

const EMPTY_FORM: ActivationFormValues = { store_id: '', reason: '', password: '' };

function formatDateTime(value: string | number | undefined): string {
  if (value === undefined || value === '') return '—';
  return new Date(value).toLocaleString('ja-JP');
}

function statusBadge(row: EmergencyElevationSummary) {
  if (row.status === 'ACTIVE') {
    return (
      <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
        有効
      </Badge>
    );
  }
  if (row.status === 'REVOKED') {
    return (
      <Badge variant="outline" className="border-transparent bg-warning/10 text-warning-strong">
        撤回済み
      </Badge>
    );
  }
  return <Badge variant="outline">期限切れ</Badge>;
}

/**
 * 緊急昇格の管理ページ（発動・履歴・撤回）。発動・履歴・撤回は同一の記録表を源とするため一頁に収める。
 * 店舗一覧の行操作には混ぜない — 緊急口を日常操作面から分離する（#832 地図の裁定）。
 */
export default function EmergencyElevationsPage() {
  const list = useCursorList(cursor => emergencyElevationApi.list({ cursor }));

  // 対象店舗の選択肢。発動フォームだけが使うため一覧とは独立に 1 回取得する
  const {
    items: stores,
    failed: storesFailed,
    refetch: refetchStores,
  } = useManagedList<PlatformStore>(() => platformAuthApi.stores());

  const form = useForm<ActivationFormValues>({ defaultValues: EMPTY_FORM });
  const {
    control,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = form;

  // 直近の発動結果。有効期限の明示は数秒で消える通知ではなく、頁に残る面で行う
  const [activation, setActivation] = useState<EmergencyElevationActivationResponse | null>(null);
  const [revoking, setRevoking] = useState<EmergencyElevationSummary | null>(null);

  const submit = async (values: ActivationFormValues) => {
    try {
      const res = await emergencyElevationApi.activate({
        store_id: Number(values.store_id),
        reason: values.reason,
        password: values.password,
      });
      // このブラウザの会話を昇格トークンへ差し替える。昇格トークンは通常の claim に昇格分を
      // 重ねた上位互換なので、平台側の操作はそのまま続けられる。期限が切れると要再ログイン
      if (res.token) {
        Cookies.set('token', res.token, { expires: new Date(res.expires_at) });
      }
      setActivation(res);
      notify.success('緊急昇格を発動しました');
      reset(EMPTY_FORM);
      list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '緊急昇格の発動に失敗しました'));
    }
  };

  const revoke = async (row: EmergencyElevationSummary) => {
    setRevoking(null);
    try {
      await emergencyElevationApi.revoke(row.id ?? 0);
      // 自分の発動を撤回した場合はここで自分のセッションも失効しており、
      // 直後の再取得が 401 で再ログインへ差し戻す（撤回の仕様どおり）
      notify.success('緊急昇格を撤回しました');
    } catch (error) {
      // 撤回操作と自然失効の競合（期限切れの瞬間を跨いだ撤回）は 400 で撥ねられる
      notify.error(getApiErrorMessage(error, '緊急昇格の撤回に失敗しました'));
    }
    list.reload();
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="緊急昇格"
        description="店長不在時などに、対象店舗の店舗コンソールの書き操作を臨時に得る緊急口です。発動・撤回はすべて記録されます。"
      />

      <Card className="p-6">
        <h2 className="text-lg font-medium text-foreground">緊急昇格を発動</h2>
        {storesFailed ? (
          <RegionError
            message="対象店舗の選択肢の取得に失敗しました"
            onRetry={() => void refetchStores()}
          />
        ) : (
          <Form {...form}>
            <form onSubmit={handleSubmit(submit)} className="max-w-lg space-y-4" noValidate>
              <FormField
                control={control}
                name="store_id"
                rules={{ required: '対象店舗を選択してください' }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>対象店舗</FormLabel>
                    <Select
                      items={stores.map(s => ({ value: String(s.id), label: s.name ?? '' }))}
                      value={field.value}
                      onValueChange={v => field.onChange(v ?? '')}
                      required
                    >
                      <FormControl>
                        <SelectTrigger className="w-full" ref={field.ref}>
                          <SelectValue placeholder="店舗を選択" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {stores.map(s => (
                          <SelectItem key={s.id} value={String(s.id)}>
                            {s.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="reason"
                rules={{
                  required: '発動の理由を入力してください',
                  maxLength: { value: 500, message: '発動の理由は 500 文字以内で入力してください' },
                }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>発動の理由</FormLabel>
                    <FormControl>
                      <Textarea
                        placeholder="例: 店長と連絡が取れず、当日受注の締め処理を代行するため"
                        required
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={control}
                name="password"
                rules={{ required: 'パスワードを入力してください' }}
                render={({ field }) => (
                  <FormItem className="gap-1">
                    <FormLabel>パスワード（再入力）</FormLabel>
                    <FormControl>
                      <Input type="password" autoComplete="current-password" required {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <p className="text-xs text-muted-foreground">
                発動するとこのブラウザの会話は有効 60
                分の昇格トークンへ切り替わり、期限が切れると再ログインが必要になります。
              </p>
              <div className="flex justify-end">
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? '発動中...' : '発動する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
        {activation && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-warning/10 p-4">
            <p className="text-sm text-warning-strong">
              緊急昇格は {formatDateTime(activation.expires_at)} まで有効です。
              期限内は対象店舗の店舗コンソールで書き操作ができます。
            </p>
            <Button render={<Link href={storeEntryPath()} />} variant="outline" size="sm">
              店舗コンソールへ入る
            </Button>
          </div>
        )}
      </Card>

      <TableCard>
        <div className="border-b bg-muted/50 px-6 py-4">
          <h2 className="text-lg font-medium text-foreground">発動履歴</h2>
        </div>
        {list.isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : list.failed ? (
          // 読めなかった履歴を残すと「発動履歴がありません」に化ける。区画自身が失敗を名乗る
          <RegionError
            message="発動履歴の取得に失敗しました"
            onRetry={() => list.reload()}
            className="justify-center p-8"
          />
        ) : list.rows.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">発動履歴がありません</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>発動者</TableHead>
                <TableHead>対象店舗</TableHead>
                <TableHead>理由</TableHead>
                <TableHead>発動時刻</TableHead>
                <TableHead>失効時刻</TableHead>
                <TableHead>状態</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.rows.map(row => (
                <TableRow key={row.id}>
                  <TableCell className="font-medium text-foreground">
                    {row.activated_by_name}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{row.store_name}</TableCell>
                  <TableCell className="max-w-64 whitespace-pre-wrap break-words text-muted-foreground">
                    {row.reason}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatDateTime(row.activated_at)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatDateTime(row.expires_at)}
                  </TableCell>
                  <TableCell>
                    {statusBadge(row)}
                    {row.status === 'REVOKED' && (
                      <div className="mt-1 text-xs text-muted-foreground">
                        {/* 撤回者は削除で欠けうるが、そのときも行は消さない */}
                        {`${row.revoked_by_name || '不明'}・${formatDateTime(row.revoked_at)}`}
                      </div>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    {/* 撤回できるのは読み時点で有効な発動だけ（期限切れ・撤回済みはサーバが撥ねる） */}
                    {row.status === 'ACTIVE' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-destructive-strong"
                        onClick={() => setRevoking(row)}
                      >
                        撤回
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {list.hasMore && (
          <div className="flex justify-center border-t p-4">
            <Button variant="outline" onClick={() => list.loadMore()} disabled={list.isLoading}>
              さらに読み込む
            </Button>
          </div>
        )}
      </TableCard>

      <ConfirmDialog
        open={revoking !== null}
        title="緊急昇格を撤回しますか？"
        description={`${revoking?.activated_by_name ?? ''} の ${revoking?.store_name ?? ''} への昇格を撤回します。発動者の全セッション（通常のログインを含む）が失効します。自分の発動を撤回した場合は、直後に再ログインが必要です。`}
        confirmLabel="撤回する"
        onConfirm={() => void (revoking && revoke(revoking))}
        onClose={() => setRevoking(null)}
      />
    </div>
  );
}
