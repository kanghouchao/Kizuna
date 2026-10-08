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
  Checkbox,
  Input,
  RegionError,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/shared/ui';

type RecoveryState = ReturnType<
  typeof useResource<Awaited<ReturnType<typeof applicantAttachmentApi.uploads>>>
>;

function recoveryFailure(
  recovery: RecoveryState,
  key?: string,
  upload?: ApplicantAttachmentUpload
) {
  const current = recovery.data?.rows.find(row => row.idempotency_key === key);
  return current ? current.failure_code : upload?.failure_code;
}

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
  // 応募者の上限20件と一覧の取得件数が一致するため、一回で対象操作を照合できる。
  const recovery = useResource(canManage ? () => applicantAttachmentApi.uploads(id) : null, [
    id,
    canManage,
  ]);
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
              recovery={recovery}
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
            recovery={recovery}
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
  recovery,
}: {
  refresh: number;
  id: string;
  policy: ApplicantAttachmentPolicy;
  editable: boolean;
  onComplete: (key: string) => void;
  recovery: RecoveryState;
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
                {recoveryFailure(recovery, upload.idempotency_key, upload) === 'CONTENT_MISMATCH'
                  ? '保存先の確認・修復待ち'
                  : upload.status === 'PENDING'
                    ? '処理結果の確認待ち'
                    : '再送が必要'}
              </p>
              {recoveryFailure(recovery, upload.idempotency_key, upload) === 'CONTENT_MISMATCH' && (
                <p className="text-sm text-destructive-strong">
                  保存済み画像の内容が一致しません。同じ画像の再送だけでは回復できません。管理者に保存先の確認・修復を依頼してください。未完了の予約は引き続き件数・容量の上限に含まれます。
                </p>
              )}
              {editable && (
                <AttachmentUploadForm
                  id={id}
                  policy={policy}
                  upload={upload}
                  onComplete={onComplete}
                  recovery={recovery}
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
  recovery,
}: {
  id: string;
  policy: ApplicantAttachmentPolicy;
  upload?: ApplicantAttachmentUpload;
  onComplete: (key: string) => void;
  onUncertain?: () => void;
  completedKey?: string | null;
  recovery: RecoveryState;
}) {
  const input = useRef<HTMLInputElement | null>(null);
  const [operation, setOperation] = useState<string | null>(null);
  const operationRef = useRef<string | null>(null);
  const inFlight = useRef(false);
  const reportedCompletion = useRef(false);
  const lifetime = useTransferLifetime();
  const key = operation ?? upload?.idempotency_key;
  const failureCode = recoveryFailure(recovery, key, upload);
  const contentMismatch = failureCode === 'CONTENT_MISMATCH';
  const form = useForm<{ file: File | null; repairConfirmed: boolean }>({
    defaultValues: { file: null, repairConfirmed: false },
  });
  const busy = form.formState.isSubmitting;
  const verifyCompletion = Boolean(
    upload &&
    operation &&
    !busy &&
    !recovery.isLoading &&
    recovery.failure === null &&
    recovery.data &&
    !recovery.data.rows.some(row => row.id === upload.id)
  );
  const completion = useResource(verifyCompletion ? () => applicantAttachmentApi.list(id) : null, [
    id,
    verifyCompletion,
    recovery.data,
  ]);
  const confirmedReady = Boolean(
    verifyCompletion && completion.data?.rows.some(row => row.id === upload?.id)
  );
  const checking =
    (Boolean(key) && recovery.isLoading) ||
    (verifyCompletion &&
      (completion.isLoading || (completion.data === null && completion.failure === null)));
  const stateUnavailable =
    (Boolean(key) && recovery.failure !== null) ||
    (verifyCompletion &&
      (completion.failure !== null ||
        (!completion.isLoading && completion.data !== null && !confirmedReady)));
  useEffect(() => {
    if (
      confirmedReady &&
      !completion.isLoading &&
      completion.failure === null &&
      upload &&
      !reportedCompletion.current
    ) {
      reportedCompletion.current = true;
      onComplete(upload.idempotency_key);
    }
  }, [confirmedReady, completion.isLoading, completion.failure, upload, onComplete]);
  const repairConfirmed = form.watch('repairConfirmed');
  const reset = form.reset;
  const setValue = form.setValue;
  useEffect(() => {
    setValue('repairConfirmed', false);
  }, [failureCode, recovery.isLoading, setValue]);
  useEffect(() => {
    if (completedKey && operation === completedKey) {
      operationRef.current = null;
      setOperation(null);
      reset();
      if (input.current) input.current.value = '';
    }
  }, [completedKey, operation, reset]);
  const submit = async ({ file }: { file: File | null }) => {
    if (!file || checking || stateUnavailable) return;
    const signal = lifetime.current!.signal;
    const key = operationRef.current ?? upload?.idempotency_key ?? createOperationKey();
    operationRef.current = key;
    setOperation(key);
    try {
      await applicantAttachmentApi.upload(id, file, key, upload?.id, signal);
      if (signal.aborted) return;
      operationRef.current = null;
      setOperation(null);
      form.reset();
      if (input.current) input.current.value = '';
      notify.success('添付画像を保存しました');
      onComplete(key);
    } catch (failure) {
      if (signal.aborted) return;
      if (contentMismatch) form.setValue('repairConfirmed', false);
      notify.error(
        getApiErrorMessage(
          failure,
          upload
            ? '回復の応答を確認できませんでした。保存状態を再確認します'
            : '画像を保存できませんでした。同じファイルで再試行してください'
        )
      );
      await recovery.reload();
      if (signal.aborted) return;
      onUncertain?.();
    }
  };
  return (
    <Form {...form}>
      <form
        noValidate
        onSubmit={event => {
          if (inFlight.current) {
            event.preventDefault();
            return;
          }
          inFlight.current = true;
          void form
            .handleSubmit(submit)(event)
            .finally(() => {
              inFlight.current = false;
            });
        }}
        className="space-y-3"
      >
        {checking && <p role="status">操作の状態を確認中...</p>}
        {stateUnavailable && (
          <RegionError
            message="操作の状態を確認できません。ファイルを保持したまま状態を再確認してください。"
            onRetry={() =>
              void (verifyCompletion && completion.failure !== null
                ? completion.reload()
                : recovery.reload())
            }
          />
        )}
        {contentMismatch && !upload && (
          <p className="text-sm text-destructive-strong">
            保存済み画像の内容が一致しません。同じ画像の再送だけでは回復できません。管理者に保存先の確認・修復を依頼してください。
          </p>
        )}
        {contentMismatch && (
          <FormField
            control={form.control}
            name="repairConfirmed"
            rules={{ validate: value => value || '保存先の確認・修復が必要です' }}
            render={({ field }) => (
              <FormItem>
                <div className="flex items-center gap-2">
                  <FormControl>
                    <Checkbox
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      onBlur={field.onBlur}
                      ref={field.ref}
                      disabled={busy}
                      required
                    />
                  </FormControl>
                  <FormLabel>管理者による保存先の確認・修復が完了している</FormLabel>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
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
        <Button
          type="submit"
          disabled={busy || checking || stateUnavailable || (contentMismatch && !repairConfirmed)}
        >
          {busy
            ? '送信中...'
            : contentMismatch
              ? '修復後の同じ画像を再送'
              : upload
                ? '同じ画像を再送'
                : '画像を追加'}
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
