import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  applicantAttachmentApi,
  type ApplicantAttachment,
  type ApplicantAttachmentUpload,
  type ApplicantAttachmentPolicy,
} from '@/entities/applicant';
import { getApiErrorMessage, useCursorList, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Input,
  RegionError,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/shared/ui';

export function ApplicantAttachmentsPanel({
  id,
  canManage,
  editable,
}: {
  id: string;
  canManage: boolean;
  editable: boolean;
}) {
  const policy = useResource(() => applicantAttachmentApi.policy());
  return (
    <section className="space-y-6 rounded-xl border bg-card p-6">
      <h2 className="text-lg font-semibold">非公開の添付画像</h2>
      {policy.failure !== null ? (
        <RegionError
          message="添付画像の設定を取得できませんでした"
          onRetry={() => void policy.reload()}
        />
      ) : policy.isLoading ? (
        <p>読み込み中...</p>
      ) : policy.data?.configured ? (
        <AttachmentContents
          id={id}
          canManage={canManage}
          editable={editable}
          policy={policy.data}
        />
      ) : (
        <p>非公開ストレージが未設定のため、添付画像は利用できません。</p>
      )}
    </section>
  );
}
function AttachmentContents({
  id,
  canManage,
  editable,
  policy,
}: {
  id: string;
  canManage: boolean;
  editable: boolean;
  policy: ApplicantAttachmentPolicy;
}) {
  const list = useCursorList(cursor => applicantAttachmentApi.list(id, cursor));
  const [refresh, setRefresh] = useState(0);
  const [completedKey, setCompletedKey] = useState<string | null>(null);
  const lifetime = useTransferLifetime();
  const [downloading, setDownloading] = useState<string | null>(null);
  const download = async (attachment: ApplicantAttachment) => {
    if (downloading) return;
    const signal = lifetime.current!.signal;
    setDownloading(attachment.id);
    try {
      const blob = await applicantAttachmentApi.download(id, attachment.id, signal);
      if (signal.aborted) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `attachment-${attachment.id}.${attachment.media_type === 'image/png' ? 'png' : 'jpg'}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      if (!signal.aborted)
        notify.error(getApiErrorMessage(error, '添付画像を取得できませんでした'));
    } finally {
      if (!signal.aborted) setDownloading(null);
    }
  };
  const completed = (key?: string) => {
    if (key) setCompletedKey(key);
    list.reload();
    setRefresh(value => value + 1);
  };
  return (
    <div className="space-y-6">
      {list.failed ? (
        <RegionError message="添付画像一覧を取得できませんでした" onRetry={list.reload} />
      ) : (
        <>
          <ul className="space-y-3">
            {list.rows.map(attachment => (
              <li
                key={attachment.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"
              >
                <div>
                  <p>
                    {attachment.media_type === 'image/png' ? 'PNG' : 'JPEG'}・
                    {Math.ceil(attachment.size_bytes / 1024)} KB
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {new Date(attachment.created_at).toLocaleString('ja-JP')}
                  </p>
                </div>
                <Button
                  variant="outline"
                  disabled={downloading !== null}
                  onClick={() => void download(attachment)}
                >
                  ダウンロード
                </Button>
              </li>
            ))}
          </ul>
          {list.isLoading ? (
            <p>読み込み中...</p>
          ) : list.rows.length === 0 ? (
            <p>添付画像はありません。</p>
          ) : null}
          {list.hasMore && (
            <Button variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
              さらに表示
            </Button>
          )}
        </>
      )}
      {canManage && (
        <>
          <p className="text-sm text-muted-foreground">
            JPEG・PNG
            の画像コピーを保存します。再変換により位置情報などのメタデータが取り除かれるため、原本や証拠資料の保管には使用できません。1件{' '}
            {Math.floor(policy.max_file_bytes / 1024 / 1024)} MB、最大 {policy.max_applicant_files}{' '}
            件です。
          </p>
          {editable ? (
            <AttachmentUploadForm
              id={id}
              policy={policy}
              onComplete={completed}
              onUncertain={completed}
              completedKey={completedKey}
            />
          ) : (
            <p>選考が終了しているため、画像の追加・再送はできません。</p>
          )}
          <PendingUploads
            refresh={refresh}
            id={id}
            policy={policy}
            editable={editable}
            onComplete={completed}
          />
        </>
      )}
    </div>
  );
}
function PendingUploads({
  refresh,
  id,
  policy,
  editable,
  onComplete,
}: {
  refresh: number;
  id: string;
  policy: ApplicantAttachmentPolicy;
  editable: boolean;
  onComplete: (key: string) => void;
}) {
  const list = useCursorList(cursor => applicantAttachmentApi.uploads(id, cursor));
  const reload = list.reload;
  useEffect(() => {
    if (refresh > 0) reload();
  }, [refresh, reload]);
  return (
    <div className="space-y-4">
      <h3 className="font-semibold">未完了のアップロード</h3>
      <p className="text-sm text-muted-foreground">
        通信が途切れた場合は、最初に選んだ同じ画像ファイルを再送してください。別の画像への置き換えはできません。未完了の予約も件数・容量の上限に含まれ、自動では削除されません。
      </p>
      {list.failed ? (
        <RegionError message="未完了のアップロードを取得できませんでした" onRetry={list.reload} />
      ) : (
        <>
          {list.rows.map(upload => (
            <div key={upload.id} className="space-y-4 rounded-lg border p-4">
              <p>
                {new Date(upload.created_at).toLocaleString('ja-JP')}・
                {upload.status === 'PENDING' ? '処理結果の確認待ち' : '再送が必要'}
              </p>
              {editable && (
                <AttachmentUploadForm
                  id={id}
                  policy={policy}
                  upload={upload}
                  onComplete={onComplete}
                />
              )}
            </div>
          ))}
          {list.isLoading ? (
            <p>読み込み中...</p>
          ) : list.rows.length === 0 ? (
            <p>未完了のアップロードはありません。</p>
          ) : null}
          {list.hasMore && (
            <Button variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
              さらに表示
            </Button>
          )}
        </>
      )}
    </div>
  );
}
function useTransferLifetime() {
  const reference = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    reference.current = controller;
    return () => controller.abort();
  }, []);
  return reference;
}
function AttachmentUploadForm({
  id,
  policy,
  upload,
  onComplete,
  onUncertain,
  completedKey,
}: {
  id: string;
  policy: ApplicantAttachmentPolicy;
  upload?: ApplicantAttachmentUpload;
  onComplete: (key: string) => void;
  onUncertain?: () => void;
  completedKey?: string | null;
}) {
  const input = useRef<HTMLInputElement | null>(null);
  const operation = useRef<string | null>(null);
  const lifetime = useTransferLifetime();
  const form = useForm<{ file: File | null }>({ defaultValues: { file: null } });
  const reset = form.reset;
  useEffect(() => {
    if (completedKey && operation.current === completedKey) {
      operation.current = null;
      reset();
      if (input.current) input.current.value = '';
    }
  }, [completedKey, reset]);
  const busy = form.formState.isSubmitting;
  const submit = async ({ file }: { file: File | null }) => {
    if (!file) return;
    const signal = lifetime.current!.signal;
    operation.current ??= upload?.idempotency_key ?? createOperationKey();
    const key = operation.current;
    try {
      await applicantAttachmentApi.upload(id, file, key, upload?.id, signal);
      if (signal.aborted) return;
      operation.current = null;
      form.reset();
      if (input.current) input.current.value = '';
      notify.success('添付画像を保存しました');
      onComplete(key);
    } catch (failure) {
      if (signal.aborted) return;
      notify.error(
        getApiErrorMessage(failure, '画像を保存できませんでした。同じファイルで再試行してください')
      );
      onUncertain?.();
    }
  };
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={event => void form.handleSubmit(submit)(event)}
        className="space-y-3"
      >
        <FormField
          control={form.control}
          name="file"
          rules={{
            validate: file =>
              !file
                ? '画像を選択してください'
                : !policy.allowed_media_types.includes(file.type)
                  ? 'JPEG または PNG の画像を選択してください'
                  : file.size === 0
                    ? '空の画像は登録できません'
                    : file.size > policy.max_file_bytes
                      ? '画像のファイル容量が上限を超えています'
                      : true,
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>{upload ? '最初に送信した画像' : '追加する画像'}</FormLabel>
              <FormControl>
                <Input
                  ref={element => {
                    field.ref(element);
                    input.current = element;
                  }}
                  name={field.name}
                  onBlur={field.onBlur}
                  type="file"
                  accept="image/jpeg,image/png"
                  required
                  disabled={busy}
                  onChange={event => field.onChange(event.target.files?.[0] ?? null)}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" disabled={busy}>
          {busy ? '送信中...' : upload ? '同じ画像を再送' : '画像を追加'}
        </Button>
      </form>
    </Form>
  );
}

function createOperationKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
