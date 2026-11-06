import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { Answer, Revision, SurveyCommand, ReceivedVia } from '@/entities/survey';
import {
  Button,
  Form,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui';
import { viaLabels } from './surveyUi';
import { SurveyTextField, SurveyChoiceField, clean } from './SurveyFields';
export type Editor =
  { kind: 'RECEIVE'; revision: Revision } | { kind: 'CORRECT'; answer: Answer; revision: Revision };
const editorTitle = (editor: Editor) =>
  ({
    RECEIVE: '回答を受付',
    CORRECT: '回答を訂正',
  })[editor.kind];
interface AnswerFormValues {
  reason: string;
  received_at: string;
  received_via: ReceivedVia;
  values: { value: string }[];
}
const validReceivedTime = (value: number) =>
  Number.isFinite(value) && value >= Date.parse('2000-01-01T00:00:00Z') && value <= Date.now();
export function SurveyEditor({
  editor,
  open,
  onOpenChange,
  disabled,
  onSubmit,
  feedback,
}: {
  editor: Editor;
  open: boolean;
  onOpenChange: (value: boolean) => void;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
  feedback: React.ReactNode;
}) {
  const [answerDraft, setAnswerDraft] = useState<AnswerFormValues>();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{editorTitle(editor)}</DialogTitle>
          <DialogDescription>
            保持期限は未設定です。不要な個人情報を入力しないでください。
          </DialogDescription>
        </DialogHeader>
        {feedback}
        <AnswerEditor
          editor={editor}
          draft={answerDraft}
          onDraft={setAnswerDraft}
          disabled={disabled}
          onSubmit={onSubmit}
        />
      </DialogContent>
    </Dialog>
  );
}
function AnswerEditor({
  editor,
  draft,
  onDraft,
  disabled,
  onSubmit,
}: {
  editor: Editor;
  draft?: AnswerFormValues;
  onDraft: (value: AnswerFormValues) => void;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
}) {
  const source = editor.kind === 'CORRECT' ? editor.answer : undefined;
  const received = source ? new Date(source.received_at) : null;
  const local = received
    ? new Date(received.getTime() - received.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
    : '';
  const form = useForm<AnswerFormValues>({
    defaultValues: draft ?? {
      reason: '',
      received_at: local,
      received_via: source?.received_via ?? ('PAPER' as ReceivedVia),
      values: editor.revision.questions.map(q => {
        const original = source?.answers.find(a => a.question_key === q.question_key);
        return { value: original?.text ?? original?.option_key ?? '' };
      }),
    },
  });
  useEffect(() => {
    return form.subscribe({
      formState: { values: true },
      callback: ({ values }) => onDraft(values),
    });
  }, [form, onDraft]);
  const [confirmation, setConfirmation] = useState<Extract<
    SurveyCommand,
    { kind: 'CORRECT' }
  > | null>(null);
  const submit = form.handleSubmit(v => {
    const at = new Date(v.received_at).getTime();
    const answers = editor.revision.questions.flatMap((q, index) => {
      const value = v.values[index]?.value;
      return value?.trim()
        ? [
            {
              question_key: q.question_key,
              ...(q.type === 'TEXT' ? { text: value } : { option_key: value }),
            },
          ]
        : [];
    });
    if (answers.length === 0) {
      form.setError(
        'values.0.value',
        { message: '少なくとも一つの回答を入力してください' },
        { shouldFocus: true }
      );
      return;
    }
    const input = {
      received_via: v.received_via,
      received_at:
        source && v.received_at === local ? source.received_at : new Date(at).toISOString(),
      answers,
    };
    if (editor.kind === 'RECEIVE')
      void onSubmit({
        kind: 'RECEIVE',
        sid: editor.revision.survey_id,
        rid: editor.revision.id,
        input: { ...input, revision_version: editor.revision.version },
      });
    else
      setConfirmation({
        kind: 'CORRECT',
        aid: editor.answer.id,
        input: { ...input, reason: v.reason, version: editor.answer.version },
      });
  });
  return (
    <>
      <Form {...form}>
        <form noValidate onSubmit={submit} className="space-y-6">
          <fieldset disabled={disabled} className="space-y-6">
            <p>
              スタッフによる記録。回答者の本人確認は行っていません。設問版{' '}
              {editor.revision.revision_number} に記録します。
            </p>
            {editor.revision.questions.map((q, index) =>
              q.type === 'TEXT' ? (
                <SurveyTextField
                  key={q.question_key}
                  control={form.control}
                  name={`values.${index}.value`}
                  label={`${q.prompt}${q.required ? '（必須）' : '（任意）'}`}
                  multiline
                  required={q.required}
                  maxLength={2000}
                  validate={v =>
                    (!q.required && !v) ||
                    clean(v, 2000, false) ||
                    '回答を1〜2000文字で入力してください'
                  }
                />
              ) : (
                <SurveyChoiceField
                  key={q.question_key}
                  control={form.control}
                  name={`values.${index}.value`}
                  label={`${q.prompt}${q.required ? '（必須）' : '（任意）'}`}
                  required={q.required}
                  items={q.options.map(o => ({ value: o.option_key, label: o.label }))}
                  disabled={disabled}
                  validate={v =>
                    (!q.required && !v) ||
                    q.options.some(o => o.option_key === v) ||
                    '回答の選択肢を選んでください'
                  }
                />
              )
            )}
            <SurveyChoiceField
              control={form.control}
              name="received_via"
              label="取得経路"
              required
              items={Object.entries(viaLabels).map(([value, label]) => ({ value, label }))}
              disabled={disabled}
              validate={v => Object.hasOwn(viaLabels, v) || '取得経路を選択してください'}
            />
            <SurveyTextField
              control={form.control}
              name="received_at"
              label="受領日時"
              required
              type="datetime-local"
              validate={v =>
                validReceivedTime(new Date(v).getTime()) ||
                '受領日時は2000年以降、現在までで指定してください'
              }
            />
            <p className="text-sm text-muted-foreground">受領日時はこの端末の時刻で入力します。</p>
            {editor.kind === 'CORRECT' && (
              <p>旧回答を取り下げ、同じ設問版に新しい回答を記録します。旧原文は残ります。</p>
            )}
            {editor.kind === 'CORRECT' && (
              <SurveyTextField
                control={form.control}
                name="reason"
                label="操作理由"
                required
                multiline
                maxLength={500}
                validate={v => clean(v, 500) || '操作理由を1〜500文字で入力してください'}
              />
            )}
            {form.formState.errors.root && (
              <p role="alert" className="text-destructive-strong">
                {form.formState.errors.root.message}
              </p>
            )}
            <Button type="submit" disabled={disabled}>
              {editor.kind === 'RECEIVE' ? '回答を記録' : '確認へ'}
            </Button>
          </fieldset>
        </form>
      </Form>
      <ConfirmDialog
        open={confirmation !== null && !disabled}
        title={editorTitle(editor)}
        description="旧回答を取り下げ、同じ版へ訂正を記録します。原文は変更しません。"
        confirmLabel="確定する"
        onClose={() => setConfirmation(null)}
        onConfirm={() => {
          if (confirmation) {
            setConfirmation(null);
            void onSubmit(confirmation);
          }
        }}
      />
    </>
  );
}
