'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  allowedApplicantTransitions,
  applicantApi,
  applicantStatusLabels,
  applicantStatusClasses,
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
import { ApplicantIntakeForm } from './ApplicantIntakeForm';
import { ApplicantInterviewForm } from './ApplicantInterviewForm';
import { ApplicantHistoryPanel } from './ApplicantHistoryPanel';
import { ApplicantTransitionForm } from './ApplicantTransitionForm';
export default function ApplicantDetailPage() {
  const { storeId, id } = useParams<{ storeId: string; id: string }>();
  const resource = useResource(() => applicantApi.get(id), [id]);
  const policy = useResource(() => applicantApi.policy());
  const [canManage, setCanManage] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [pendingMode, setPendingMode] = useState<
    'intake' | 'interview' | 'close' | 'reload' | null
  >(null);
  const [mode, setMode] = useState<'intake' | 'interview' | null>(null);
  useEffect(() => {
    const claims = readTokenClaims();
    setCanManage(
      hasPermission(claims, 'RECRUITMENT_VIEW') && hasPermission(claims, 'RECRUITMENT_MANAGE')
    );
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
    setDirty(false);
    setMode(pendingMode === 'close' || pendingMode === 'reload' ? null : pendingMode);
    if (pendingMode === 'reload') void resource.reload();
    setPendingMode(null);
  };
  const applicant = resource.data;
  const changed = (value: ApplicantDetail) => {
    setDirty(false);
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
        <Button variant="outline" render={<Link href={storePath(storeId, '/applicants')} />}>
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
            fallback={{ href: storePath(storeId, '/applicants'), label: '一覧へ' }}
          />
        ) : (
          <RegionError
            message="応募者情報の取得に失敗しました"
            onRetry={() => void resource.reload()}
          />
        )}
      </div>
    );
  const editable = canManage && allowedApplicantTransitions(applicant.status).length > 0;
  return (
    <div className="space-y-6">
      {header}
      <section className="rounded-xl border bg-card p-6 space-y-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold break-words">{applicant.name}</h2>
            <Badge variant="outline" className={applicantStatusClasses[applicant.status]}>
              {applicantStatusLabels[applicant.status]}
            </Badge>
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
          onDirtyChange={setDirty}
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
            onDirtyChange={setDirty}
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
            onDirtyChange={setDirty}
            onSave={(status, reason) =>
              save(
                () => applicantApi.transition(id, applicant.version, status, reason),
                '選考状態を変更しました'
              )
            }
          />
        </section>
      )}
      <section className="rounded-xl border bg-card p-6 space-y-4">
        <h2 className="text-lg font-semibold">採否確定・情報保持方針</h2>
        {policy.failure !== null ? (
          <RegionError
            message="採用方針の取得に失敗しました"
            onRetry={() => void policy.reload()}
          />
        ) : policy.isLoading ? (
          <p>読み込み中...</p>
        ) : (
          <>
            <p>
              {policy.data?.final_decision_configured
                ? '採否確定は専用権限が必要です。'
                : '最終責任者が未設定のため、採用・不採用は確定できません。'}
            </p>
            <Button disabled>採否を確定</Button>
            <p className="text-sm text-muted-foreground">
              保存期間・匿名化期限は未設定です。自動削除は行いません。
            </p>
          </>
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
      <ApplicantHistoryPanel key={`history:${id}:${applicant.version}`} id={id} />
    </div>
  );
}
