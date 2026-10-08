'use client';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useParams } from 'next/navigation';
import { surveyApi, SurveyCommand, Revision } from '@/entities/survey';
import {
  readTokenClaims,
  hasPermission,
  getApiErrorMessage,
  isBadRequest,
  isConflict,
  isNotFound,
} from '@/shared/lib';
import { Button } from '@/shared/ui';
import { notify } from '@/shared/notify';
import {
  SurveyCatalogue,
  SurveyVersions,
  SurveyRevisionView,
  SurveyAnswerView,
} from './SurveyViews';
import { SurveyEditor, Editor } from './SurveyEditor';
import { accessError, View } from './surveyUi';
const subscribe = () => () => {};
export function SurveysPage() {
  const mounted = useSyncExternalStore(
    subscribe,
    () => true,
    () => false
  );
  const { storeId } = useParams<{ storeId: string }>();
  if (!mounted) return <p>読み込み中...</p>;
  const claims = readTokenClaims();
  if (claims?.userType !== 'STAFF' || !hasPermission(claims, 'SURVEY_VIEW'))
    return <div role="alert">アンケートの閲覧権限がありません</div>;
  return (
    <Surveys
      key={storeId}
      canManage={hasPermission(claims, 'SURVEY_MANAGE')}
      canRecord={hasPermission(claims, 'SURVEY_RECORD')}
    />
  );
}
function Surveys({ canManage, canRecord }: { canManage: boolean; canRecord: boolean }) {
  const [view, setView] = useState<View>({ kind: 'catalogue' });
  const [epoch, setEpoch] = useState(0);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [editorKey, setEditorKey] = useState(0);
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState<SurveyCommand | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [revisionCandidate, setRevisionCandidate] = useState<{
    revision: Revision;
    draftId?: string;
  } | null>(null);
  const [denied, setDenied] = useState<string | null>(null);
  const lock = useRef(false),
    active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const deny = (message: string) => {
    if (!active.current) return;
    setDenied(message);
    setEditor(null);
    setPending(null);
    setUncertain(false);
    setOpen(false);
  };
  const submit = async (command: SurveyCommand) => {
    if (lock.current || denied) return;
    lock.current = true;
    setBusy(true);
    setPending(command);
    try {
      const result = await surveyApi.write(command);
      if (!active.current) return;
      setPending(null);
      setUncertain(false);
      setConflict(false);
      setMissing(false);
      setEditor(null);
      setOpen(false);
      setEpoch(n => n + 1);
      setView(
        'answer' in result
          ? { kind: 'answer', aid: result.answer.id }
          : { kind: 'revision', sid: result.revision.survey_id, rid: result.revision.id }
      );
      notify.success('アンケートの操作を記録しました');
    } catch (error) {
      if (!active.current) return;
      const denial = accessError(error);
      if (denial) {
        deny(denial);
      } else if (isBadRequest(error) || isConflict(error) || isNotFound(error)) {
        setPending(null);
        setUncertain(false);
        setConflict(isConflict(error));
        setMissing(isNotFound(error));
      } else setUncertain(true);
      notify.error(getApiErrorMessage(error, '操作結果が確認できません'));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  const navigate = (next: View) => {
    if (busy || pending) return;
    setEditor(null);
    setOpen(false);
    setConflict(false);
    setMissing(false);
    setRefreshError(null);
    setRevisionCandidate(null);
    setView(next);
  };
  const refresh = async () => {
    if (!editor || busy) return;
    setBusy(true);
    setRefreshError(null);
    let parentFound = false;
    try {
      let updated: Editor = editor;
      if (editor.kind === 'REVISE') {
        const survey = await surveyApi.survey(editor.revision.survey_id);
        parentFound = true;
        const latest = await surveyApi.revision(survey.id, survey.latest_revision_id);
        if (!active.current) return;
        setRevisionCandidate({ revision: latest, draftId: survey.draft_revision_id });
        setEpoch(n => n + 1);
        return;
      }
      if ('answer' in editor) {
        const answer = await surveyApi.answer(editor.answer.id);
        updated =
          editor.kind === 'CORRECT'
            ? {
                ...editor,
                answer,
                revision: await surveyApi.revision(answer.survey_id, answer.revision_id),
              }
            : { ...editor, answer };
      } else if ('revision' in editor) {
        updated = {
          ...editor,
          revision: await surveyApi.revision(editor.revision.survey_id, editor.revision.id),
        };
      }
      if (!active.current) return;
      setEditor(updated);
      setEpoch(n => n + 1);
      setConflict(false);
      setMissing(false);
    } catch (error) {
      if (!active.current) return;
      const denial = accessError(error);
      if (denial) deny(denial);
      else if (isNotFound(error) && !parentFound) {
        setEditor(null);
        setOpen(false);
        setView({ kind: 'catalogue' });
        setEpoch(n => n + 1);
        setMissing(false);
        setConflict(false);
      } else
        setRefreshError(
          getApiErrorMessage(error, '最新状態を確認できません。入力は保持しています')
        );
    } finally {
      if (active.current) setBusy(false);
    }
  };
  const edit = (value: Editor) => {
    setRefreshError(null);
    setRevisionCandidate(null);
    setEditor(value);
    setEditorKey(n => n + 1);
    setOpen(true);
    setConflict(false);
    setMissing(false);
  };
  const disabled = busy || pending !== null;
  const allowed =
    !editor ||
    editor.kind === 'CREATE' ||
    editor.kind === 'REVISE' ||
    (editor.kind === 'CORRECT' && !editor.answer.superseded_by_id) ||
    (editor.kind === 'WITHDRAW' && editor.answer.status === 'ACTIVE') ||
    (editor.kind === 'REPLACE' && editor.revision.status === 'DRAFT') ||
    (editor.kind === 'OPEN' && editor.revision.status === 'DRAFT') ||
    (editor.kind === 'CLOSE' && editor.revision.status !== 'CLOSED') ||
    (editor.kind === 'RECEIVE' && editor.revision.status === 'OPEN');
  const feedback = (
    <>
      {refreshError && <p role="alert">{refreshError}</p>}
      {revisionCandidate && editor?.kind === 'REVISE' && (
        <div role="status" className="rounded-lg border p-4 space-y-3">
          <p>
            最新版は版 {revisionCandidate.revision.revision_number}{' '}
            です。入力した改版案は保持しています。
          </p>
          {revisionCandidate.draftId ? (
            <p>未完了の下書きがあります。版一覧でその下書きを確認してください。</p>
          ) : (
            <Button
              disabled={busy}
              variant="outline"
              onClick={() => {
                setEditor({ ...editor, revision: revisionCandidate.revision });
                setRevisionCandidate(null);
                setConflict(false);
                setMissing(false);
              }}
            >
              この最新版を改版元にする
            </Button>
          )}
        </div>
      )}
      {pending && uncertain && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>結果が確認できません。同じ要求で結果を確認するまで、新しい操作はできません。</p>
          <Button variant="outline" disabled={busy} onClick={() => void submit(pending)}>
            同じ要求で結果を確認
          </Button>
        </div>
      )}
      {(conflict || missing) && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>
            {missing
              ? '対象が見つかりません。入力は保持しています。'
              : '状態が更新されています。入力を保持したまま最新状態を確認してください。'}
          </p>
          <Button variant="outline" disabled={busy} onClick={() => void refresh()}>
            最新状態を確認
          </Button>
        </div>
      )}
      {!allowed && (
        <p role="alert">
          現在の状態ではこの操作を実行できません。入力内容を確認し、対象の版・回答から操作を選び直してください。
        </p>
      )}
    </>
  );
  const props = { navigate, edit, denied: deny, canManage, canRecord, disabled };
  if (denied) return <div role="alert">{denied}。再ログインまたは権限を確認してください。</div>;
  return (
    <div className="space-y-6">
      {view.kind !== 'catalogue' && (
        <Button
          variant="outline"
          disabled={disabled}
          onClick={() => navigate({ kind: 'catalogue' })}
        >
          アンケート一覧へ
        </Button>
      )}
      {!open && feedback}
      {editor && !open && (
        <Button variant="outline" onClick={() => setOpen(true)}>
          入力画面へ戻る
        </Button>
      )}
      <div key={`${JSON.stringify(view)}-${epoch}`}>
        {view.kind === 'catalogue' ? (
          <SurveyCatalogue {...props} />
        ) : view.kind === 'survey' ? (
          <SurveyVersions sid={view.sid} {...props} />
        ) : view.kind === 'revision' ? (
          <SurveyRevisionView sid={view.sid} rid={view.rid} {...props} />
        ) : (
          <SurveyAnswerView aid={view.aid} {...props} />
        )}
      </div>
      {editor && (
        <SurveyEditor
          key={editorKey}
          editor={editor}
          open={open}
          onOpenChange={setOpen}
          disabled={disabled || conflict || missing || revisionCandidate !== null || !allowed}
          onSubmit={submit}
          feedback={feedback}
        />
      )}
    </div>
  );
}
