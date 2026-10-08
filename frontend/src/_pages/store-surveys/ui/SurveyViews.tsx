import { useState } from 'react';
import { AnswerSearch, SurveySearch, surveyApi } from '@/entities/survey';
import { useListPage, useResource, useCursorList } from '@/shared/lib';
import {
  Button,
  Input,
  Label,
  Table,
  TableHead,
  TableHeader,
  TableRow,
  TableBody,
  TableCell,
  RegionError,
  Badge,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import {
  Choice,
  revisionLabels,
  answerLabels,
  viaLabels,
  operationLabels,
  time,
  View,
  useGuardedRead,
} from './surveyUi';
import { Editor } from './SurveyEditor';
export interface ViewProps {
  navigate: (view: View) => void;
  edit: (editor: Editor) => void;
  denied: (message: string) => void;
  canManage: boolean;
  canRecord: boolean;
  disabled: boolean;
}
export function SurveyCatalogue(props: ViewProps) {
  const guard = useGuardedRead(props.denied);
  const [search, setSearch] = useState<SurveySearch>({});
  const list = useListPage(
    (page, criteria: SurveySearch) => guard(() => surveyApi.list(page, criteria)),
    {}
  );
  return (
    <ListPage
      title="アンケート管理"
      description="設問を版管理し、スタッフが既に受け取った回答を記録します。保持期限は未設定です。"
      actions={
        props.canManage && (
          <Button disabled={props.disabled} onClick={() => props.edit({ kind: 'CREATE' })}>
            アンケートを作成
          </Button>
        )
      }
      state={list}
      search={{
        content: (
          <>
            <div className="space-y-2">
              <Label htmlFor="survey-search">最新版の題名</Label>
              <Input
                id="survey-search"
                value={search.q ?? ''}
                maxLength={120}
                onChange={e => setSearch({ ...search, q: e.target.value })}
              />
            </div>
            <Choice
              id="survey-status"
              label="最新版の状態"
              value={search.latest_status ?? ''}
              optional
              items={revisionLabels}
              onChange={v =>
                setSearch({
                  ...search,
                  latest_status: (v as SurveySearch['latest_status']) || undefined,
                })
              }
            />
            <Choice
              id="survey-sort"
              label="並び順"
              value={search.sort ?? 'CREATED_DESC'}
              items={{ CREATED_DESC: '作成が新しい順', CREATED_ASC: '作成が古い順' }}
              onChange={v => setSearch({ ...search, sort: v as SurveySearch['sort'] })}
            />
            <Button type="submit">検索</Button>
          </>
        ),
        onSearch: () => void list.search({ ...search, q: search.q?.trim() || undefined }),
      }}
      emptyMessage="アンケートはありません"
      errorMessage="アンケート一覧を取得できませんでした"
      onRetry={() => void list.reload()}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>最新版の題名</TableHead>
            <TableHead>版</TableHead>
            <TableHead>状態</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.rows.map(row => (
            <TableRow key={row.id}>
              <TableCell className="max-w-80 break-words">{row.latest_title}</TableCell>
              <TableCell>{row.latest_revision_number}</TableCell>
              <TableCell>
                {revisionLabels[row.latest_status]}
                {row.open_revision_id &&
                  row.open_revision_id !== row.latest_revision_id &&
                  '（旧版を受付中）'}
              </TableCell>
              <TableCell>
                <Button
                  variant="outline"
                  disabled={props.disabled}
                  onClick={() => props.navigate({ kind: 'survey', sid: row.id })}
                >
                  版と回答
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
export function SurveyVersions({ sid, ...props }: ViewProps & { sid: string }) {
  const guard = useGuardedRead(props.denied);
  const detail = useResource(() => guard(() => surveyApi.survey(sid)), [sid]);
  const list = useListPage(page => guard(() => surveyApi.revisions(sid, page), 'list'));
  if (detail.failure === 'notFound')
    return (
      <div role="alert" className="space-y-3">
        <p>アンケートが見つかりません</p>
        <Button variant="outline" onClick={() => props.navigate({ kind: 'catalogue' })}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure)
    return <RegionError message="アンケートを取得できませんでした" onRetry={detail.reload} />;
  if (!detail.data || detail.isLoading) return <p>読み込み中...</p>;
  return (
    <ListPage
      title={detail.data.latest_title}
      description="設問の文面は開始後に固定されます。受付中の旧版を終了してから、新版を開始してください。"
      state={list}
      emptyMessage="設問版はありません"
      errorMessage="設問版を取得できませんでした"
      onRetry={() => void list.reload()}
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
            <TableRow key={row.id}>
              <TableCell>{row.revision_number}</TableCell>
              <TableCell className="max-w-80 break-words">{row.title}</TableCell>
              <TableCell>{revisionLabels[row.status]}</TableCell>
              <TableCell>
                <Button
                  variant="outline"
                  disabled={props.disabled}
                  onClick={() => props.navigate({ kind: 'revision', sid, rid: row.id })}
                >
                  設問・回答
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ListPage>
  );
}
export function SurveyRevisionView({
  sid,
  rid,
  ...props
}: ViewProps & { sid: string; rid: string }) {
  const guard = useGuardedRead(props.denied);
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
  const [search, setSearch] = useState<AnswerSearch>({});
  const list = useListPage(
    (page, criteria: AnswerSearch) =>
      guard(() => surveyApi.answers(sid, rid, page, criteria), 'list'),
    {}
  );
  if (detail.failure === 'notFound')
    return (
      <div role="alert" className="space-y-3">
        <p>設問版が見つかりません</p>
        <Button variant="outline" onClick={() => props.navigate({ kind: 'catalogue' })}>
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
      <div className="flex flex-wrap gap-3 items-center">
        <Badge variant="outline">{revisionLabels[revision.status]}</Badge>
        <span>設問版 {revision.revision_number}</span>
        {props.canManage && (
          <>
            {revision.status === 'DRAFT' && (
              <>
                <Button
                  variant="outline"
                  disabled={props.disabled}
                  onClick={() => props.edit({ kind: 'REPLACE', revision })}
                >
                  下書きを編集
                </Button>
                <Button
                  disabled={props.disabled || !!series.open_revision_id}
                  onClick={() => props.edit({ kind: 'OPEN', revision })}
                >
                  受付を開始
                </Button>
              </>
            )}
            {revision.status !== 'CLOSED' && (
              <Button
                variant="outline"
                disabled={props.disabled}
                onClick={() => props.edit({ kind: 'CLOSE', revision })}
              >
                受付を終了
              </Button>
            )}
            {revision.id === series.latest_revision_id && !series.draft_revision_id && (
              <Button
                variant="outline"
                disabled={props.disabled}
                onClick={() => props.edit({ kind: 'REVISE', revision })}
              >
                新しい版を作成
              </Button>
            )}
          </>
        )}
      </div>
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
      <ListPage
        title={`${revision.title} — 回答記録`}
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
        onRetry={() => void list.reload()}
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
                  <Button
                    variant="outline"
                    disabled={props.disabled}
                    onClick={() => props.navigate({ kind: 'answer', aid: row.id })}
                  >
                    回答・履歴
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <SurveyHistoryView target={{ sid, rid }} {...props} />
    </div>
  );
}
export function SurveyAnswerView({ aid, ...props }: ViewProps & { aid: string }) {
  const guard = useGuardedRead(props.denied);
  const detail = useResource(
    () =>
      guard(async () => {
        const answer = await surveyApi.answer(aid);
        const revision = await surveyApi.revision(answer.survey_id, answer.revision_id);
        return { answer, revision };
      }),
    [aid]
  );
  if (detail.failure === 'notFound')
    return (
      <div role="alert" className="space-y-3">
        <p>回答が見つかりません</p>
        <Button variant="outline" onClick={() => props.navigate({ kind: 'catalogue' })}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure)
    return <RegionError message="回答を取得できませんでした" onRetry={detail.reload} />;
  if (!detail.data || detail.isLoading) return <p>読み込み中...</p>;
  const { answer, revision } = detail.data;
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">回答 #{answer.id}</h1>
      <p>スタッフによる記録。回答者の本人確認は行っていません。保持期限は未設定です。</p>
      <p>
        {answerLabels[answer.status]} ／ 設問版 {answer.revision_number} ／ 受領{' '}
        {time(answer.received_at)} ／ {viaLabels[answer.received_via]}
      </p>
      <p>
        記録者: {answer.recorded_by.display_name} ／ 記録 {time(answer.created_at)}
      </p>
      <div className="flex gap-3 flex-wrap">
        <Button
          variant="outline"
          disabled={props.disabled}
          onClick={() =>
            props.navigate({ kind: 'revision', sid: answer.survey_id, rid: answer.revision_id })
          }
        >
          設問版へ戻る
        </Button>
        {props.canRecord && (
          <>
            {answer.status === 'ACTIVE' && (
              <Button
                variant="outline"
                disabled={props.disabled}
                onClick={() => props.edit({ kind: 'WITHDRAW', answer })}
              >
                回答を取り下げ
              </Button>
            )}
            {!answer.superseded_by_id && (
              <Button
                variant="outline"
                disabled={props.disabled}
                onClick={() => props.edit({ kind: 'CORRECT', answer, revision })}
              >
                回答を訂正
              </Button>
            )}
          </>
        )}
        {answer.supersedes_id && (
          <Button
            variant="outline"
            disabled={props.disabled}
            onClick={() => props.navigate({ kind: 'answer', aid: answer.supersedes_id! })}
          >
            訂正元を表示
          </Button>
        )}
        {answer.superseded_by_id && (
          <Button
            variant="outline"
            disabled={props.disabled}
            onClick={() => props.navigate({ kind: 'answer', aid: answer.superseded_by_id! })}
          >
            訂正先を表示
          </Button>
        )}
      </div>
      <dl className="space-y-6">
        {revision.questions.map(q => {
          const value = answer.answers.find(a => a.question_key === q.question_key);
          return (
            <div key={q.question_key} className="rounded-lg border p-4 space-y-2">
              <dt className="font-semibold whitespace-pre-wrap wrap-anywhere">{q.prompt}</dt>
              <dd className="whitespace-pre-wrap wrap-anywhere">
                {value
                  ? (value.text ??
                    q.options.find(o => o.option_key === value.option_key)?.label ??
                    '選択肢を確認できません')
                  : '未回答'}
              </dd>
            </div>
          );
        })}
      </dl>
      <SurveyHistoryView target={{ aid }} {...props} />
    </div>
  );
}
function SurveyHistoryView({
  target,
  ...props
}: ViewProps & { target: { sid: string; rid: string } | { aid: string } }) {
  const guard = useGuardedRead(props.denied);
  const history = useCursorList(cursor => guard(() => surveyApi.history(target, cursor)));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">操作履歴</h2>
      {history.failed ? (
        <RegionError message="履歴を取得できませんでした" onRetry={history.reload} />
      ) : history.isLoading ? (
        <p>読み込み中...</p>
      ) : (
        <ol className="space-y-3">
          {history.rows.map(row => (
            <li key={row.id} className="rounded-lg border p-4 space-y-2">
              <p>
                {operationLabels[row.type]} ／ {time(row.created_at)} ／ {row.actor.display_name}
              </p>
              {row.reason && <p className="whitespace-pre-wrap wrap-anywhere">{row.reason}</p>}
              {row.related_response_id && (
                <Button
                  variant="outline"
                  disabled={props.disabled}
                  onClick={() => props.navigate({ kind: 'answer', aid: row.related_response_id! })}
                >
                  関連回答 #{row.related_response_id}
                </Button>
              )}
            </li>
          ))}
        </ol>
      )}
      {history.hasMore && !history.failed && (
        <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
          履歴をさらに表示
        </Button>
      )}
    </section>
  );
}
