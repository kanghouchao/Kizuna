'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useStoreContext } from '@/entities/user';
import { useEffect, useState } from 'react';
import {
  allowedApplicantTransitions,
  applicantApi,
  applicantStatusLabels,
  applicantStatusClasses,
  anonymizedBadgeClass,
  receptionChannelLabels,
  sourceTypeLabels,
  type ApplicantDetail,
} from '@/entities/applicant';
import {
  getApiErrorMessage,
  hasPermission,
  readTokenClaims,
  storePath,
  useResource,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { Badge, Button, ConfirmDialog, RegionError } from '@/shared/ui';
import { ApplicantAttachmentsPanel } from './ApplicantAttachmentsPanel';
import { ApplicantDecisionForm } from './ApplicantDecisionForm';
import { ApplicantIntakeForm } from './ApplicantIntakeForm';
import { ApplicantInterviewForm } from './ApplicantInterviewForm';
import { ApplicantHistoryPanel } from './ApplicantHistoryPanel';
import { ApplicantTransitionForm } from './ApplicantTransitionForm';
export default function ApplicantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const storeId = useStoreContext().currentStoreId;
  const resource = useResource(() => applicantApi.get(id), [id]);
  const policy = useResource(() => applicantApi.policy());
  const [canManage, setCanManage] = useState(false);
  const [canDecide, setCanDecide] = useState(false);
  const [canEnroll, setCanEnroll] = useState(false);
  const [attachments, setAttachments] = useState({ read: false, write: false });
  const [saving, setSaving] = useState(false);
  const [formDirty, setFormDirty] = useState(false);
  const [decisionDirty, setDecisionDirty] = useState(false);
  const dirty = formDirty || decisionDirty;
  const clearDirty = () => {
    setFormDirty(false);
    setDecisionDirty(false);
  };
  const [pendingMode, setPendingMode] = useState<
    'intake' | 'interview' | 'close' | 'reload' | null
  >(null);
  const [mode, setMode] = useState<'intake' | 'interview' | null>(null);
  useEffect(() => {
    const claims = readTokenClaims();
    const read =
      hasPermission(claims, 'RECRUITMENT_VIEW') &&
      hasPermission(claims, 'RECRUITMENT_ATTACHMENT_VIEW');
    setAttachments({ read, write: read && hasPermission(claims, 'RECRUITMENT_ATTACHMENT_MANAGE') });
    setCanManage(
      hasPermission(claims, 'RECRUITMENT_VIEW') && hasPermission(claims, 'RECRUITMENT_MANAGE')
    );
    setCanDecide(
      hasPermission(claims, 'RECRUITMENT_VIEW') && hasPermission(claims, 'RECRUITMENT_DECIDE')
    );
    setCanEnroll(hasPermission(claims, 'RECRUITMENT_VIEW') && hasPermission(claims, 'CAST_MANAGE'));
  }, []);
  const move = (next: 'intake' | 'interview' | 'close' | 'reload') => {
    if (saving) return;
    if (dirty) {
      setPendingMode(next);
      return;
    }
    setMode(next === 'close' || next === 'reload' ? null : next);
    if (next === 'reload') void resource.reload();
  };
  const discardAndMove = () => {
    if (!pendingMode || saving) return;
    clearDirty();
    setMode(pendingMode === 'close' || pendingMode === 'reload' ? null : pendingMode);
    if (pendingMode === 'reload') void resource.reload();
    setPendingMode(null);
  };
  const applicant = resource.data;
  const changed = (value: ApplicantDetail) => {
    clearDirty();
    resource.setData(value);
    setMode(null);
  };
  const save = async (operation: () => Promise<ApplicantDetail>, success: string) => {
    if (saving) return;
    setSaving(true);
    try {
      changed(await operation());
      notify.success(success);
    } catch (error) {
      notify.error(getApiErrorMessage(error, '保存に失敗しました'));
    } finally {
      setSaving(false);
    }
  };
  const header = (
    <div className="flex items-center justify-between gap-3">
      <h1 className="text-2xl font-bold">応募者詳細</h1>
      <div className="flex gap-3">
        {resource.failure !== 'notFound' && (
          <Button variant="outline" disabled={saving} onClick={() => move('reload')}>
            再読み込み
          </Button>
        )}
        <Button variant="outline" render={<Link href={storePath('/applicants')} />}>
          一覧へ
        </Button>
      </div>
    </div>
  );
  if (resource.isLoading)
    return (
      <div className="space-y-6">
        {header}
        <p>読み込み中...</p>
      </div>
    );
  if (resource.failure !== null || !applicant)
    return (
      <div className="space-y-6">
        {header}
        {resource.failure === 'notFound' ? (
          <RegionError
            message="応募者が見つかりませんでした"
            fallback={{ href: storePath('/applicants'), label: '一覧へ' }}
          />
        ) : (
          <RegionError
            message="応募者情報の取得に失敗しました"
            onRetry={() => void resource.reload()}
          />
        )}
      </div>
    );
  const open = allowedApplicantTransitions(applicant.status).length > 0;
  const editable = canManage && open;
  return (
    <div className="space-y-6">
      {header}
      <section className="rounded-xl border bg-card p-6 space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold break-words">{applicant.name}</h2>
            <div className="flex flex-wrap gap-1">
              <Badge variant="outline" className={applicantStatusClasses[applicant.status]}>
                {applicantStatusLabels[applicant.status]}
              </Badge>
              {applicant.anonymized_at && (
                <Badge variant="outline" className={anonymizedBadgeClass}>
                  匿名化済み
                </Badge>
              )}
            </div>
            {!open && (
              <p className="mt-2 text-sm text-muted-foreground">
                選考は終了しました。編集・再開はできません。
              </p>
            )}
            {applicant.anonymized_at && (
              <p className="mt-2 text-sm text-muted-foreground">
                保持期間の満了により {new Date(applicant.anonymized_at).toLocaleString('ja-JP')}{' '}
                に匿名化しました。氏名・連絡先・経歴・面接メモ・添付は削除済みで、選考の経緯だけを残しています。
              </p>
            )}
          </div>
          {editable && (
            <Button
              variant="outline"
              disabled={saving}
              onClick={() => move(mode === 'intake' ? 'close' : 'intake')}
            >
              {mode === 'intake' ? '編集を閉じる' : '受付情報を編集'}
            </Button>
          )}
        </div>
        <dl className="grid gap-6 md:grid-cols-2">
          {[
            ['受付チャネル', receptionChannelLabels[applicant.channel]],
            ['応募元区分', sourceTypeLabels[applicant.source_type]],
            ['応募元媒体', applicant.source_media],
            ['紹介者・スカウト名', applicant.referrer],
            ['担当者名（記録用）', applicant.assignee],
            ['電話番号', applicant.phone],
            ['メールアドレス', applicant.email],
            ['住所', applicant.address],
            ['経歴', applicant.experience],
            ['希望条件', applicant.desired_conditions],
          ].map(([label, value]) => (
            <div key={label}>
              <dt className="text-sm text-muted-foreground">{label}</dt>
              <dd className="mt-1 whitespace-pre-wrap break-words">{value || '未設定'}</dd>
            </div>
          ))}
        </dl>
        <p className="text-sm text-muted-foreground">
          最終更新 {new Date(applicant.updated_at).toLocaleString('ja-JP')}・操作ユーザー ID{' '}
          {applicant.modified_by}
        </p>
      </section>
      {mode === 'intake' && editable && (
        <ApplicantIntakeForm
          key={`intake:${id}:${applicant.version}`}
          initial={applicant}
          onDirtyChange={setFormDirty}
          onSave={data =>
            save(() => applicantApi.update(id, applicant.version, data), '受付情報を保存しました')
          }
        />
      )}
      <section className="rounded-xl border bg-card p-6 space-y-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-semibold">面接記録</h2>
          {editable && (
            <Button
              variant="outline"
              disabled={saving}
              onClick={() => move(mode === 'interview' ? 'close' : 'interview')}
            >
              {mode === 'interview' ? '編集を閉じる' : '面接記録を編集'}
            </Button>
          )}
        </div>
        <p className="text-sm text-muted-foreground">
          1件の面接記録を保持します。複数回の面接履歴と本人確認書類の保存には対応していません。
        </p>
        {mode === 'interview' && editable ? (
          <ApplicantInterviewForm
            key={`interview:${id}:${applicant.version}`}
            initial={applicant.interview}
            onDirtyChange={setFormDirty}
            onSave={data =>
              save(
                () => applicantApi.interview(id, applicant.version, data),
                '面接記録を保存しました'
              )
            }
          />
        ) : applicant.interview ? (
          <div className="space-y-3">
            <p>
              {new Date(applicant.interview.interview_at).toLocaleString('ja-JP')}・
              {applicant.interview.interviewer}
            </p>
            <p className="whitespace-pre-wrap break-words">
              {applicant.interview.notes || 'メモなし'}
            </p>
            <ul className="space-y-1">
              {Object.entries(applicant.interview.checklist).map(([label, checked]) => (
                <li key={label} className="break-words">
                  {checked ? '確認済み' : '未確認'}：{label}
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <p>面接記録は未登録です。</p>
        )}
      </section>
      {editable && mode === null && (
        <section className="rounded-xl border bg-card p-6 space-y-4">
          <h2 className="text-lg font-semibold">選考状態の変更</h2>
          <ApplicantTransitionForm
            key={`transition:${id}:${applicant.version}`}
            applicant={applicant}
            busy={saving}
            onDirtyChange={setFormDirty}
            onSave={(status, reason) =>
              save(
                () => applicantApi.transition(id, applicant.version, status, reason),
                '選考状態を変更しました'
              )
            }
          />
        </section>
      )}
      {canDecide && open && mode === null && (
        <section className="rounded-xl border bg-card p-6 space-y-4">
          <h2 className="text-lg font-semibold">採否の確定</h2>
          <ApplicantDecisionForm
            key={`decision:${id}:${applicant.version}`}
            applicant={applicant}
            busy={saving}
            onDirtyChange={setDecisionDirty}
            onSave={(status, reason) =>
              save(
                () => applicantApi.decide(id, applicant.version, status, reason),
                '採否を確定しました'
              )
            }
          />
        </section>
      )}
      {applicant.status === 'HIRED' && (
        <section className="rounded-xl border bg-card p-6 space-y-4">
          <h2 className="text-lg font-semibold">在籍</h2>
          {applicant.enrollment_id ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm">在籍登録済みです。招待はキャスト一覧から発行します。</p>
              <Button
                variant="outline"
                render={<Link href={storePath(`/casts/${applicant.enrollment_id}/edit`)} />}
              >
                在籍を開く
              </Button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-muted-foreground">
                {applicant.anonymized_at
                  ? '在籍は未登録です。匿名化済みのため在籍登録はできません。'
                  : '在籍は未登録です。'}
              </p>
              {canEnroll && !applicant.anonymized_at && (
                <Button render={<Link href={storePath(`/applicants/${id}/enrollment`)} />}>
                  在籍登録
                </Button>
              )}
            </div>
          )}
        </section>
      )}
      <section className="rounded-xl border bg-card p-6 space-y-4">
        <h2 className="text-lg font-semibold">情報保持方針</h2>
        {policy.failure !== null ? (
          <RegionError
            message="採用方針の取得に失敗しました"
            onRetry={() => void policy.reload()}
          />
        ) : policy.isLoading ? (
          <p>読み込み中...</p>
        ) : (
          policy.data && (
            <>
              <dl className="grid gap-6 md:grid-cols-3">
                {(
                  [
                    ['REJECTED', policy.data.retention_periods.rejected_months],
                    ['WITHDRAWN', policy.data.retention_periods.withdrawn_months],
                    ['HIRED', policy.data.retention_periods.hired_months],
                  ] as const
                ).map(([status, months]) => (
                  <div key={status}>
                    <dt className="text-sm text-muted-foreground">
                      {applicantStatusLabels[status]}
                    </dt>
                    <dd className="mt-1">
                      {months === null ? '自動匿名化しない' : `保持 ${months} か月`}
                    </dd>
                  </div>
                ))}
              </dl>
              <p className="text-sm text-muted-foreground">
                期間は終端状態へ確定した時点から数えます。保持期間はシステム設定で管理します。
              </p>
            </>
          )
        )}
      </section>
      <ConfirmDialog
        open={pendingMode !== null}
        title="未保存の入力を破棄しますか？"
        description="編集内容は保存されていません。"
        confirmLabel="破棄して続ける"
        onClose={() => setPendingMode(null)}
        onConfirm={discardAndMove}
      />
      {attachments.read && (
        <ApplicantAttachmentsPanel
          key={`attachments:${storeId}:${id}`}
          id={id}
          canManage={attachments.write}
          editable={open}
        />
      )}
      <ApplicantHistoryPanel key={`history:${id}:${applicant.version}`} id={id} />
    </div>
  );
}
