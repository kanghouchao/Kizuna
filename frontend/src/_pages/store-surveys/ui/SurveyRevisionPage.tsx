'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { AnswerSearch, Revision, Survey, surveyApi } from '@/entities/survey';
import { useListPage, useResource } from '@/shared/lib';
import {
  Badge,
  Button,
  RegionError,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import {
  answerHref,
  reviseHref,
  revisionEditHref,
  revisionHref,
  surveyListHref,
} from '../lib/surveyHref';
import { SurveyCapabilities, SurveyDenied, SurveyGate } from './SurveyGate';
import { SurveyHistory } from './SurveyHistory';
import { SurveyTableSection } from './SurveyTableSection';
import { Choice, answerLabels, revisionLabels, time, useGuardedRead, viaLabels } from './surveyUi';
import { SurveyTask, useSurveyWorkspace } from './useSurveyWorkspace';

export function SurveyRevisionPage() {
  const { sid, rid } = useParams<{ sid: string; rid: string }>();
  return (
    <SurveyGate target={`${sid}:${rid}`}>
      {caps => <RevisionScreen sid={sid} rid={rid} {...caps} />}
    </SurveyGate>
  );
}

interface DetailProps extends SurveyCapabilities {
  sid: string;
  rid: string;
  edit: (task: SurveyTask) => void;
  deny: (message: string) => void;
  disabled: boolean;
}

function RevisionScreen({ sid, rid, ...caps }: SurveyCapabilities & { sid: string; rid: string }) {
  const workspace = useSurveyWorkspace(revisionHref(sid, rid));
  if (workspace.denied) return <SurveyDenied message={workspace.denied} />;
  return (
    <div className="space-y-6">
      {workspace.status}
      <RevisionDetail
        key={workspace.epoch}
        sid={sid}
        rid={rid}
        edit={workspace.edit}
        deny={workspace.deny}
        disabled={workspace.disabled}
        {...caps}
      />
      {workspace.dialog}
    </div>
  );
}

function RevisionDetail({ sid, rid, ...props }: DetailProps) {
  const guard = useGuardedRead(props.deny);
  const detail = useResource(
    () =>
      guard(async () => {
        const [revision, series, counts] = await Promise.all([
          surveyApi.revision(sid, rid),
          surveyApi.survey(sid),
          surveyApi.counts(sid, rid),
        ]);
        return { revision, series, counts };
      }),
    [sid, rid]
  );
  if (detail.failure === 'notFound')
    return (
      <div role="alert" className="space-y-3">
        <p>設問版が見つかりません</p>
        <Button variant="outline" render={<Link href={surveyListHref} />}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure)
    return <RegionError message="設問版を取得できませんでした" onRetry={detail.reload} />;
  if (!detail.data || detail.isLoading) return <p>読み込み中...</p>;
  const { revision, series, counts } = detail.data;
  return (
    <div className="space-y-6">
      <Button variant="outline" render={<Link href={surveyListHref} />}>
        アンケート一覧へ
      </Button>
      <div className="space-y-2">
        <h1 className="text-2xl font-bold wrap-anywhere">{revision.title}</h1>
        <p className="flex flex-wrap items-center gap-3">
          <Badge variant="outline">{revisionLabels[revision.status]}</Badge>
          <span>設問版 {revision.revision_number}</span>
        </p>
      </div>
      <RevisionActions revision={revision} series={series} sid={sid} rid={rid} {...props} />
      <section className="rounded-lg border p-4 space-y-3">
        <h2 className="text-lg font-semibold">設問の内容</h2>
        <ol className="space-y-3">
          {revision.questions.map(q => (
            <li key={q.question_key} className="whitespace-pre-wrap wrap-anywhere">
              <p>
                {q.prompt}（{q.required ? '必須' : '任意'}）
              </p>
              {q.type === 'SINGLE_CHOICE' && <p>{q.options.map(o => o.label).join(' ／ ')}</p>}
            </li>
          ))}
        </ol>
      </section>
      <section className="rounded-lg border p-4 space-y-2">
        <h2 className="text-lg font-semibold">この版の記録件数</h2>
        <p>
          全記録 {counts.total_records} 件 ／ 有効 {counts.active_records} 件 ／ 取り下げ{' '}
          {counts.withdrawn_records} 件
        </p>
        <p>
          回答者数や回答率ではありません。訂正前の記録も全記録に含みます。保持期限は未設定です。
        </p>
      </section>
      <AnswerList revision={revision} sid={sid} rid={rid} {...props} />
      <RevisionList sid={sid} rid={rid} deny={props.deny} />
      <SurveyHistory target={{ sid, rid }} denied={props.deny} />
    </div>
  );
}

function RevisionActions({
  revision,
  series,
  sid,
  rid,
  canManage,
  edit,
  disabled,
}: DetailProps & { revision: Revision; series: Survey }) {
  if (!canManage) return null;
  return (
    <div className="flex flex-wrap gap-3">
      {revision.status === 'DRAFT' && (
        <>
          <Button variant="outline" render={<Link href={revisionEditHref(sid, rid)} />}>
            下書きを編集
          </Button>
          <Button
            disabled={disabled || !!series.open_revision_id}
            onClick={() => edit({ kind: 'OPEN', revision })}
          >
            受付を開始
          </Button>
        </>
      )}
      {revision.status !== 'CLOSED' && (
        <Button
          variant="outline"
          disabled={disabled}
          onClick={() => edit({ kind: 'CLOSE', revision })}
        >
          受付を終了
        </Button>
      )}
      {revision.id === series.latest_revision_id && !series.draft_revision_id && (
        <Button variant="outline" render={<Link href={reviseHref(sid, rid)} />}>
          新しい版を作成
        </Button>
      )}
    </div>
  );
}

function AnswerList({ revision, sid, rid, ...props }: DetailProps & { revision: Revision }) {
  const guard = useGuardedRead(props.deny);
  const [search, setSearch] = useState<AnswerSearch>({});
  const list = useListPage(
    (page, criteria: AnswerSearch) => guard(() => surveyApi.answers(sid, rid, page, criteria)),
    {}
  );
  return (
    <SurveyTableSection
      title="回答記録"
      description="スタッフによる記録。回答者の本人確認は行っていません。"
      actions={
        props.canRecord &&
        revision.status === 'OPEN' && (
          <Button
            disabled={props.disabled}
            onClick={() => props.edit({ kind: 'RECEIVE', revision })}
          >
            回答を受付
          </Button>
        )
      }
      state={list}
      search={{
        content: (
          <>
            <Choice
              id="answer-status"
              label="回答の状態"
              value={search.status ?? ''}
              optional
              items={answerLabels}
              onChange={v =>
                setSearch({ ...search, status: (v as AnswerSearch['status']) || undefined })
              }
            />
            <Choice
              id="answer-sort"
              label="回答の並び順"
              value={search.sort ?? 'RECEIVED_DESC'}
              items={{
                RECEIVED_DESC: '受領が新しい順',
                RECEIVED_ASC: '受領が古い順',
                CREATED_DESC: '記録が新しい順',
                CREATED_ASC: '記録が古い順',
              }}
              onChange={v => setSearch({ ...search, sort: v as AnswerSearch['sort'] })}
            />
            <Button type="submit">検索</Button>
          </>
        ),
        onSearch: () => void list.search(search),
      }}
      emptyMessage="回答記録はありません"
      errorMessage="回答一覧を取得できませんでした"
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>回答ID</TableHead>
            <TableHead>受領日時</TableHead>
            <TableHead>取得経路</TableHead>
            <TableHead>状態</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id}>
              <TableCell>{row.id}</TableCell>
              <TableCell>{time(row.received_at)}</TableCell>
              <TableCell>{viaLabels[row.received_via]}</TableCell>
              <TableCell>{answerLabels[row.status]}</TableCell>
              <TableCell>
                <Button variant="outline" render={<Link href={answerHref(row.id)} />}>
                  回答・履歴
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </SurveyTableSection>
  );
}

function RevisionList({
  sid,
  rid,
  deny,
}: {
  sid: string;
  rid: string;
  deny: (message: string) => void;
}) {
  const guard = useGuardedRead(deny);
  const list = useListPage(page => guard(() => surveyApi.revisions(sid, page)));
  return (
    <SurveyTableSection
      title="設問版"
      description="設問の文面は開始後に固定されます。受付中の旧版を終了してから、新版を開始してください。"
      state={list}
      emptyMessage="設問版はありません"
      errorMessage="設問版を取得できませんでした"
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>版</TableHead>
            <TableHead>題名</TableHead>
            <TableHead>状態</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id} aria-current={row.id === rid ? 'page' : undefined}>
              <TableCell>{row.revision_number}</TableCell>
              <TableCell className="max-w-80 break-words">{row.title}</TableCell>
              <TableCell>{revisionLabels[row.status]}</TableCell>
              <TableCell>
                {row.id === rid ? (
                  <span className="text-muted-foreground">表示中</span>
                ) : (
                  <Button variant="outline" render={<Link href={revisionHref(sid, row.id)} />}>
                    設問・回答
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </SurveyTableSection>
  );
}
