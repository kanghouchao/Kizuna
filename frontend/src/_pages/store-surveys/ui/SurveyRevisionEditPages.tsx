'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { Revision, surveyApi } from '@/entities/survey';
import { isNotFound, useResource } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { revisionHref, surveyListHref, writtenHref } from '../lib/surveyHref';
import { useSurveyRefresh } from '../lib/useSurveyRefresh';
import { useSurveyWrite } from '../lib/useSurveyWrite';
import { SurveyDefinitionForm } from './SurveyDefinitionForm';
import { SurveyEditFrame, editCard } from './SurveyEditFrame';
import { SurveyDenied, SurveyGate } from './SurveyGate';
import { SurveyWriteFeedback } from './SurveyWriteFeedback';
import { useGuardedRead } from './surveyUi';
import { useCurrentStoreId } from '@/entities/user';

export function SurveyReplacePage() {
  const { sid, rid } = useParams<{ sid: string; rid: string }>();
  return (
    <SurveyGate target={`${sid}:${rid}`} manage>
      {() => <Replace sid={sid} rid={rid} />}
    </SurveyGate>
  );
}

export function SurveyRevisePage() {
  const { sid } = useParams<{ sid: string }>();
  const from = useSearchParams().get('from');
  return (
    <SurveyGate target={`${sid}:${from}`} manage>
      {() => <Revise sid={sid} from={from} />}
    </SurveyGate>
  );
}

function RevisionMissing() {
  const storeId = useCurrentStoreId();
  return (
    <div role="alert" className="space-y-3">
      <p>設問版が見つかりません</p>
      <Button variant="outline" render={<Link href={surveyListHref(storeId)} />}>
        一覧へ戻る
      </Button>
    </div>
  );
}

/** DRAFT の版を直す。読み直した版が DRAFT でなくなっていれば、入力を残して保存だけを止める。 */
function Replace({ sid, rid }: { sid: string; rid: string }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const write = useSurveyWrite(result => router.replace(writtenHref(storeId, result)));
  const refresher = useSurveyRefresh(write, () => router.replace(surveyListHref(storeId)));
  const guard = useGuardedRead(write.deny);
  const detail = useResource(() => guard(() => surveyApi.revision(sid, rid)), [sid, rid]);
  const [latest, setLatest] = useState<Revision | null>(null);
  if (write.denied) return <SurveyDenied message={write.denied} />;
  const back = { href: revisionHref(storeId, sid, rid), label: '設問版へ戻る' };
  const opened = detail.data;
  const current = latest ?? opened;
  const allowed = current?.status === 'DRAFT';
  return (
    <SurveyEditFrame
      title="下書きを編集"
      back={back}
      feedback={
        <SurveyWriteFeedback
          write={write}
          busy={write.busy || refresher.refreshing}
          refreshError={refresher.refreshError}
          onRefresh={() =>
            void refresher.refresh(async () => setLatest(await surveyApi.revision(sid, rid)))
          }
        >
          {opened?.status === 'DRAFT' && !allowed && (
            <p role="alert">
              現在の状態ではこの操作を実行できません。入力内容を確認し、対象の版から操作を選び直してください。
            </p>
          )}
        </SurveyWriteFeedback>
      }
    >
      {detail.failure === 'notFound' ? (
        <RevisionMissing />
      ) : detail.failure ? (
        <RegionError message="設問版を取得できませんでした" onRetry={detail.reload} />
      ) : !opened || !current ? (
        <p>読み込み中...</p>
      ) : opened.status !== 'DRAFT' ? (
        <RegionError message="下書きではないため編集できません" fallback={back} />
      ) : (
        <section className={editCard}>
          <SurveyDefinitionForm
            initial={{ title: opened.title, questions: opened.questions }}
            disabled={refresher.locked || !allowed}
            onSubmit={input =>
              void write.submit({
                kind: 'REPLACE',
                sid,
                rid,
                input: { ...input, version: current.version },
              })
            }
          />
        </section>
      )}
    </SurveyEditFrame>
  );
}

/**
 * 系列の最新版で下書きが無いときだけ、その版から新しい DRAFT を起こす。改版元が古くなったら最新版を
 * 示し、入力を保ったまま改版元を差し替えるかは利用者が選ぶ。
 */
function Revise({ sid, from }: { sid: string; from: string | null }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const write = useSurveyWrite(result => router.replace(writtenHref(storeId, result)));
  const refresher = useSurveyRefresh(write, () => router.replace(surveyListHref(storeId)));
  const guard = useGuardedRead(write.deny);
  const detail = useResource(
    from
      ? () =>
          guard(async () => {
            const [revision, series] = await Promise.all([
              surveyApi.revision(sid, from),
              surveyApi.survey(sid),
            ]);
            return { revision, series };
          })
      : null,
    [sid, from]
  );
  const [base, setBase] = useState<Revision | null>(null);
  const [candidate, setCandidate] = useState<{ revision: Revision; draftId?: string } | null>(null);
  if (write.denied) return <SurveyDenied message={write.denied} />;
  const back = from
    ? { href: revisionHref(storeId, sid, from), label: '設問版へ戻る' }
    : { href: surveyListHref(storeId), label: 'アンケート一覧へ' };
  const opened = detail.data;
  const current = base ?? opened?.revision;
  const refresh = () => {
    let parentFound = false;
    void refresher.refresh(
      async () => {
        const series = await surveyApi.survey(sid);
        parentFound = true;
        const latest = await surveyApi.revision(series.id, series.latest_revision_id);
        setCandidate({ revision: latest, draftId: series.draft_revision_id });
      },
      error => isNotFound(error) && !parentFound
    );
  };
  return (
    <SurveyEditFrame
      title="新しい版を作成"
      back={back}
      feedback={
        <SurveyWriteFeedback
          write={write}
          busy={write.busy || refresher.refreshing}
          refreshError={refresher.refreshError}
          onRefresh={refresh}
        >
          {candidate && (
            <div role="status" className="rounded-lg border p-4 space-y-3">
              <p>
                最新版は版 {candidate.revision.revision_number}{' '}
                です。入力した改版案は保持しています。
              </p>
              {candidate.draftId ? (
                <p>未完了の下書きがあります。設問版の一覧でその下書きを確認してください。</p>
              ) : (
                <Button
                  disabled={write.busy}
                  variant="outline"
                  onClick={() => {
                    setBase(candidate.revision);
                    setCandidate(null);
                    write.settle();
                  }}
                >
                  この最新版を改版元にする
                </Button>
              )}
            </div>
          )}
        </SurveyWriteFeedback>
      }
    >
      {!from || detail.failure === 'notFound' ? (
        <RevisionMissing />
      ) : detail.failure ? (
        <RegionError message="設問版を取得できませんでした" onRetry={detail.reload} />
      ) : !opened || !current ? (
        <p>読み込み中...</p>
      ) : opened.revision.id !== opened.series.latest_revision_id ||
        opened.series.draft_revision_id ? (
        <RegionError
          message="この版からは新しい版を作成できません。最新版で、下書きが無いときだけ作成できます"
          fallback={back}
        />
      ) : (
        <section className={editCard}>
          <SurveyDefinitionForm
            initial={{ title: opened.revision.title, questions: opened.revision.questions }}
            disabled={refresher.locked || candidate !== null}
            onSubmit={input =>
              void write.submit({
                kind: 'REVISE',
                sid,
                input: { ...input, based_on_revision_id: current.id },
              })
            }
          />
        </section>
      )}
    </SurveyEditFrame>
  );
}
