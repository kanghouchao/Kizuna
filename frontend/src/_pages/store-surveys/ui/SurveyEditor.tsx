import { useState, useEffect } from 'react';
import {
  useForm,
  useFieldArray,
  Control,
  UseFormRegister,
  UseFormSetValue,
  useWatch,
} from 'react-hook-form';
import {
  Answer,
  Revision,
  DefinitionInput,
  Question,
  SurveyCommand,
  ReceivedVia,
} from '@/entities/survey';
import {
  Button,
  Input,
  Label,
  Textarea,
  Checkbox,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui';
import { Choice, requestKey, viaLabels } from './surveyUi';
export type Editor =
  | { kind: 'CREATE' }
  | { [K in 'REVISE' | 'REPLACE' | 'OPEN' | 'CLOSE' | 'RECEIVE']: { kind: K; revision: Revision } }[
      'REVISE' | 'REPLACE' | 'OPEN' | 'CLOSE' | 'RECEIVE']
  | { kind: 'CORRECT'; answer: Answer; revision: Revision }
  | { kind: 'WITHDRAW'; answer: Answer };
export const editorTitle = (editor: Editor) =>
  ({
    CREATE: 'アンケートを作成',
    REVISE: '新しい版を作成',
    REPLACE: '下書きを編集',
    OPEN: '受付を開始',
    CLOSE: '受付を終了',
    RECEIVE: '回答を受付',
    CORRECT: '回答を訂正',
    WITHDRAW: '回答を取り下げ',
  })[editor.kind];
interface AnswerFormValues {
  reason: string;
  received_at: string;
  received_via: ReceivedVia;
  values: Record<string, string>;
}
const clean = (value: string, max: number, trim = true) => {
  const text = value.replace(/\r\n?/g, '\n');
  return (
    text.trim().length > 0 && (trim ? text.trim() : text).length <= max && !text.includes('\0')
  );
};
const validReceivedTime = (value: number) =>
  Number.isFinite(value) && value >= Date.parse('2000-01-01T00:00:00Z') && value <= Date.now();
const blankQuestion = (): Question => ({
  question_key: `q${requestKey()}`,
  type: 'TEXT',
  prompt: '',
  required: true,
  options: [],
});
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
  const [definitionDraft, setDefinitionDraft] = useState<DefinitionInput>();
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
        {editor.kind === 'CREATE' || editor.kind === 'REVISE' || editor.kind === 'REPLACE' ? (
          <DefinitionEditor
            editor={editor}
            draft={definitionDraft}
            onDraft={setDefinitionDraft}
            disabled={disabled}
            onSubmit={onSubmit}
          />
        ) : (
          <AnswerEditor
            editor={editor}
            draft={answerDraft}
            onDraft={setAnswerDraft}
            disabled={disabled}
            onSubmit={onSubmit}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
function DefinitionEditor({
  editor,
  draft,
  onDraft,
  disabled,
  onSubmit,
}: {
  editor: Extract<Editor, { kind: 'CREATE' | 'REVISE' | 'REPLACE' }>;
  draft?: DefinitionInput;
  onDraft: (value: DefinitionInput) => void;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
}) {
  const form = useForm<DefinitionInput>({
    defaultValues:
      draft ??
      (editor.kind === 'CREATE'
        ? { title: '', questions: [blankQuestion()] }
        : { title: editor.revision.title, questions: editor.revision.questions }),
  });
  useEffect(() => {
    return form.subscribe({
      formState: { values: true },
      callback: ({ values }) => onDraft(values),
    });
  }, [form, onDraft]);
  const questions = useFieldArray({ control: form.control, name: 'questions' });
  const submit = form.handleSubmit(values => {
    if (
      !clean(values.title, 120) ||
      values.questions.length < 1 ||
      values.questions.length > 20 ||
      values.questions.some(
        q =>
          !clean(q.prompt, 500) ||
          (q.type === 'SINGLE_CHOICE' &&
            (q.options.length < 2 ||
              q.options.length > 10 ||
              q.options.some(o => !clean(o.label, 120)) ||
              new Set(q.options.map(o => o.label.trim())).size !== q.options.length))
      )
    ) {
      form.setError('root', { message: '題名・設問・選択肢の必須項目と上限を確認してください' });
      return;
    }
    const input = { ...values, dedupe_key: requestKey() };
    if (editor.kind === 'CREATE') void onSubmit({ kind: 'CREATE', input });
    else if (editor.kind === 'REVISE')
      void onSubmit({
        kind: 'REVISE',
        sid: editor.revision.survey_id,
        input: { ...input, based_on_revision_id: editor.revision.id },
      });
    else
      void onSubmit({
        kind: 'REPLACE',
        sid: editor.revision.survey_id,
        rid: editor.revision.id,
        input: { ...input, version: editor.revision.version },
      });
  });
  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <fieldset disabled={disabled} className="space-y-6">
        <div className="space-y-2">
          <Label htmlFor="survey-title">題名</Label>
          <Input id="survey-title" {...form.register('title')} maxLength={120} required />
        </div>
        {questions.fields.map((q, index) => (
          <section key={q.id} className="rounded-lg border p-4 space-y-3">
            <h2 className="text-lg font-semibold">設問 {index + 1}</h2>
            <QuestionEditor
              index={index}
              control={form.control}
              register={form.register}
              setValue={form.setValue}
              disabled={disabled}
            />
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={disabled || index === 0}
                onClick={() => questions.move(index, index - 1)}
              >
                設問を上へ
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={disabled || index === questions.fields.length - 1}
                onClick={() => questions.move(index, index + 1)}
              >
                設問を下へ
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={disabled || questions.fields.length === 1}
                onClick={() => questions.remove(index)}
              >
                設問を除去
              </Button>
            </div>
          </section>
        ))}
        <Button
          type="button"
          variant="outline"
          disabled={disabled || questions.fields.length >= 20}
          onClick={() => questions.append(blankQuestion())}
        >
          設問を追加
        </Button>
        {form.formState.errors.root && (
          <p role="alert" className="text-destructive-strong">
            {form.formState.errors.root.message}
          </p>
        )}
        <p>開始後は設問を変更できません。変更は新しい版として作成します。</p>
        <Button type="submit" disabled={disabled}>
          下書きを保存
        </Button>
      </fieldset>
    </form>
  );
}
function QuestionEditor({
  index,
  control,
  register,
  setValue,
  disabled,
}: {
  index: number;
  control: Control<DefinitionInput>;
  register: UseFormRegister<DefinitionInput>;
  setValue: UseFormSetValue<DefinitionInput>;
  disabled: boolean;
}) {
  const row = useWatch({ control, name: `questions.${index}` });
  const options = useFieldArray({ control, name: `questions.${index}.options` });
  return (
    <>
      <div className="space-y-2">
        <Label htmlFor={`question-${index}`}>設問文</Label>
        <Textarea
          id={`question-${index}`}
          {...register(`questions.${index}.prompt`)}
          maxLength={500}
          required
        />
      </div>
      <Choice
        id={`type-${index}`}
        label="回答形式"
        value={row.type}
        items={{ TEXT: 'テキスト', SINGLE_CHOICE: '単一選択' }}
        disabled={disabled}
        onChange={v => {
          setValue(`questions.${index}.type`, v as Question['type']);
          options.replace(
            v === 'TEXT'
              ? []
              : [
                  { option_key: `o${requestKey()}`, label: '' },
                  { option_key: `o${requestKey()}`, label: '' },
                ]
          );
        }}
      />
      <div className="flex gap-2 items-center">
        <Checkbox
          id={`required-${index}`}
          checked={row.required}
          disabled={disabled}
          onCheckedChange={v => setValue(`questions.${index}.required`, v === true)}
        />
        <Label htmlFor={`required-${index}`}>回答必須</Label>
      </div>
      {row.type === 'SINGLE_CHOICE' && (
        <div className="space-y-3">
          {options.fields.map((o, i) => (
            <div key={o.id} className="space-y-2">
              <Label htmlFor={`option-${index}-${i}`}>選択肢 {i + 1}</Label>
              <Input
                id={`option-${index}-${i}`}
                {...register(`questions.${index}.options.${i}.label`)}
                maxLength={120}
                required
              />
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={disabled || i === 0}
                  onClick={() => options.move(i, i - 1)}
                >
                  選択肢を上へ
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={disabled || options.fields.length <= 2}
                  onClick={() => options.remove(i)}
                >
                  選択肢を除去
                </Button>
              </div>
            </div>
          ))}
          <Button
            type="button"
            variant="outline"
            disabled={disabled || options.fields.length >= 10}
            onClick={() => options.append({ option_key: `o${requestKey()}`, label: '' })}
          >
            選択肢を追加
          </Button>
        </div>
      )}
    </>
  );
}
function AnswerEditor({
  editor,
  draft,
  onDraft,
  disabled,
  onSubmit,
}: {
  editor: Exclude<Editor, { kind: 'CREATE' | 'REVISE' | 'REPLACE' }>;
  draft?: AnswerFormValues;
  onDraft: (value: AnswerFormValues) => void;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
}) {
  const intake = editor.kind === 'RECEIVE' || editor.kind === 'CORRECT';
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
      values: Object.fromEntries(
        source?.answers.map(a => [a.question_key, a.text ?? a.option_key ?? '']) ?? []
      ) as Record<string, string>,
    },
  });
  useEffect(() => {
    return form.subscribe({
      formState: { values: true },
      callback: ({ values }) => onDraft(values),
    });
  }, [form, onDraft]);
  const [confirmation, setConfirmation] = useState<SurveyCommand | null>(null);
  const values = useWatch({ control: form.control });
  const submit = form.handleSubmit(v => {
    if (editor.kind !== 'RECEIVE' && !clean(v.reason, 500)) {
      form.setError('root', { message: '操作理由を1〜500文字で入力してください' });
      return;
    }
    const base = {
      reason: v.reason,
      version: 'answer' in editor ? editor.answer.version : editor.revision.version,
      dedupe_key: requestKey(),
    };
    let command: SurveyCommand;
    if (intake) {
      const at = new Date(v.received_at).getTime();
      if (!validReceivedTime(at)) {
        form.setError('root', { message: '受領日時は2000年以降、現在までで指定してください' });
        return;
      }
      const questions = editor.revision.questions;
      if (
        questions.some(
          q =>
            (q.required && !v.values[q.question_key]?.trim()) ||
            (v.values[q.question_key] &&
              q.type === 'TEXT' &&
              !clean(v.values[q.question_key], 2000, false))
        )
      ) {
        form.setError('root', { message: '必須の回答とテキスト2000文字以内を確認してください' });
        return;
      }
      const answers = questions
        .filter(q => v.values[q.question_key]?.trim())
        .map(q => ({
          question_key: q.question_key,
          ...(q.type === 'TEXT'
            ? { text: v.values[q.question_key] }
            : { option_key: v.values[q.question_key] }),
        }));
      if (answers.length === 0) {
        form.setError('root', { message: '少なくとも一つの回答を入力してください' });
        return;
      }
      const input = {
        received_via: v.received_via,
        received_at: new Date(at).toISOString(),
        answers,
        dedupe_key: base.dedupe_key,
      };
      command =
        editor.kind === 'RECEIVE'
          ? {
              kind: 'RECEIVE',
              sid: editor.revision.survey_id,
              rid: editor.revision.id,
              input: { ...input, revision_version: editor.revision.version },
            }
          : { kind: 'CORRECT', aid: editor.answer.id, input: { ...input, ...base } };
    } else if (editor.kind === 'WITHDRAW')
      command = { kind: 'WITHDRAW', aid: editor.answer.id, input: base };
    else
      command = {
        kind: editor.kind as 'OPEN' | 'CLOSE',
        sid: editor.revision.survey_id,
        rid: editor.revision.id,
        input: base,
      };
    if (editor.kind === 'RECEIVE') void onSubmit(command);
    else setConfirmation(command);
  });
  return (
    <>
      <form noValidate onSubmit={submit} className="space-y-6">
        <fieldset disabled={disabled} className="space-y-6">
          {intake && (
            <>
              <p>
                スタッフによる記録。回答者の本人確認は行っていません。設問版{' '}
                {editor.revision.revision_number} に記録します。
              </p>
              {editor.revision.questions.map(q => (
                <div key={q.question_key} className="space-y-2">
                  {q.type === 'TEXT' ? (
                    <>
                      <Label htmlFor={`answer-${q.question_key}`}>
                        {q.prompt}
                        {q.required ? '（必須）' : '（任意）'}
                      </Label>
                      <Textarea
                        id={`answer-${q.question_key}`}
                        {...form.register(`values.${q.question_key}`)}
                        required={q.required}
                        maxLength={2000}
                      />
                    </>
                  ) : (
                    <Choice
                      id={`answer-${q.question_key}`}
                      label={`${q.prompt}${q.required ? '（必須）' : '（任意）'}`}
                      value={values.values?.[q.question_key] ?? ''}
                      items={Object.fromEntries(q.options.map(o => [o.option_key, o.label]))}
                      disabled={disabled}
                      optional={!q.required}
                      onChange={v => form.setValue(`values.${q.question_key}`, v)}
                    />
                  )}
                </div>
              ))}
              <Choice
                id="survey-received-via"
                label="取得経路"
                value={values.received_via ?? 'PAPER'}
                items={viaLabels}
                disabled={disabled}
                onChange={v => form.setValue('received_via', v as ReceivedVia)}
              />
              <div className="space-y-2">
                <Label htmlFor="survey-received-at">受領日時</Label>
                <Input
                  id="survey-received-at"
                  type="datetime-local"
                  {...form.register('received_at')}
                  required
                />
              </div>
            </>
          )}
          {editor.kind === 'CORRECT' && (
            <p>旧回答を取り下げ、同じ設問版に新しい回答を記録します。旧原文は残ります。</p>
          )}
          {editor.kind === 'WITHDRAW' && (
            <p>取り下げ後は復帰できません。原文と履歴は残り、個人情報の消去ではありません。</p>
          )}
          {editor.kind !== 'RECEIVE' && (
            <div className="space-y-2">
              <Label htmlFor="survey-reason">操作理由</Label>
              <Textarea id="survey-reason" {...form.register('reason')} required maxLength={500} />
            </div>
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
      <ConfirmDialog
        open={confirmation !== null && !disabled}
        title={editorTitle(editor)}
        description={
          editor.kind === 'CORRECT'
            ? '旧回答を取り下げ、同じ版へ訂正を記録します。原文は変更しません。'
            : '理由と対象の版・回答を確認して確定してください。'
        }
        confirmLabel="確定する"
        onClose={() => setConfirmation(null)}
        onConfirm={() => {
          if (confirmation) void onSubmit(confirmation);
        }}
      />
    </>
  );
}
