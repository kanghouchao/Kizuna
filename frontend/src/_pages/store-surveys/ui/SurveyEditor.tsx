import { useState, useEffect } from 'react';
import { useForm, useFieldArray, Control, useWatch, useFormContext } from 'react-hook-form';
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
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
  Checkbox,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui';
import { requestKey, viaLabels } from './surveyUi';
import { SurveyTextField, SurveyChoiceField } from './SurveyFields';
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
  values: { value: string }[];
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
    <Form {...form}>
      <form onSubmit={submit} noValidate className="space-y-6">
        <fieldset disabled={disabled} className="space-y-6">
          <SurveyTextField
            control={form.control}
            name="title"
            label="題名"
            required
            maxLength={120}
            validate={v => clean(v, 120) || '題名を1〜120文字で入力してください'}
          />
          {questions.fields.map((q, index) => (
            <section key={q.id} className="rounded-lg border p-4 space-y-3">
              <h2 className="text-lg font-semibold">設問 {index + 1}</h2>
              <QuestionEditor index={index} control={form.control} disabled={disabled} />
              <div className="flex flex-wrap gap-2">
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
    </Form>
  );
}
function QuestionEditor({
  index,
  control,
  disabled,
}: {
  index: number;
  control: Control<DefinitionInput>;
  disabled: boolean;
}) {
  const { getValues } = useFormContext<DefinitionInput>();
  const row = useWatch({ control, name: `questions.${index}` });
  const options = useFieldArray({ control, name: `questions.${index}.options` });
  return (
    <>
      <SurveyTextField
        control={control}
        name={`questions.${index}.prompt`}
        label="設問文"
        required
        multiline
        maxLength={500}
        validate={v => clean(v, 500) || '設問文を1〜500文字で入力してください'}
      />
      <SurveyChoiceField
        control={control}
        name={`questions.${index}.type`}
        label="回答形式"
        required
        items={[
          { value: 'TEXT', label: 'テキスト' },
          { value: 'SINGLE_CHOICE', label: '単一選択' },
        ]}
        disabled={disabled}
        validate={v => ['TEXT', 'SINGLE_CHOICE'].includes(v) || '回答形式を選択してください'}
        onChange={v =>
          options.replace(
            v === 'TEXT'
              ? []
              : [
                  { option_key: `o${requestKey()}`, label: '' },
                  { option_key: `o${requestKey()}`, label: '' },
                ]
          )
        }
      />
      <FormField
        control={control}
        name={`questions.${index}.required`}
        render={({ field }) => (
          <FormItem className="flex gap-2 items-center">
            <FormControl>
              <Checkbox
                checked={field.value}
                ref={field.ref}
                onBlur={field.onBlur}
                disabled={disabled}
                onCheckedChange={v => field.onChange(v === true)}
              />
            </FormControl>
            <FormLabel>回答必須</FormLabel>
            <FormMessage />
          </FormItem>
        )}
      />
      {row.type === 'SINGLE_CHOICE' && (
        <div className="space-y-3">
          {options.fields.map((o, i) => (
            <div key={o.id} className="space-y-2">
              <SurveyTextField
                control={control}
                name={`questions.${index}.options.${i}.label`}
                label={`選択肢 ${i + 1}`}
                required
                maxLength={120}
                validate={v =>
                  !clean(v, 120)
                    ? '選択肢を1〜120文字で入力してください'
                    : getValues(`questions.${index}.options`).some(
                          (other, n) => n !== i && other.label.trim() === v.trim()
                        )
                      ? '同じ文面の選択肢を重複させないでください'
                      : true
                }
              />
              <div className="flex flex-wrap gap-2">
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
      values: intake
        ? editor.revision.questions.map(q => {
            const original = source?.answers.find(a => a.question_key === q.question_key);
            return { value: original?.text ?? original?.option_key ?? '' };
          })
        : [],
    },
  });
  useEffect(() => {
    return form.subscribe({
      formState: { values: true },
      callback: ({ values }) => onDraft(values),
    });
  }, [form, onDraft]);
  const [confirmation, setConfirmation] = useState<SurveyCommand | null>(null);
  const submit = form.handleSubmit(v => {
    const base = {
      reason: v.reason,
      version: 'answer' in editor ? editor.answer.version : editor.revision.version,
      dedupe_key: requestKey(),
    };
    let command: SurveyCommand;
    if (intake) {
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
      <Form {...form}>
        <form noValidate onSubmit={submit} className="space-y-6">
          <fieldset disabled={disabled} className="space-y-6">
            {intake && (
              <>
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
                <p className="text-sm text-muted-foreground">
                  受領日時はこの端末の時刻で入力します。
                </p>
              </>
            )}
            {editor.kind === 'CORRECT' && (
              <p>旧回答を取り下げ、同じ設問版に新しい回答を記録します。旧原文は残ります。</p>
            )}
            {editor.kind === 'WITHDRAW' && (
              <p>取り下げ後は復帰できません。原文と履歴は残り、個人情報の消去ではありません。</p>
            )}
            {editor.kind !== 'RECEIVE' && (
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
        description={
          editor.kind === 'CORRECT'
            ? '旧回答を取り下げ、同じ版へ訂正を記録します。原文は変更しません。'
            : '理由と対象の版・回答を確認して確定してください。'
        }
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
