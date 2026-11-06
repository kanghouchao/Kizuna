import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Answer, ReceivedVia, Revision, SurveyCommand } from '@/entities/survey';
import { Button, ConfirmDialog, Form } from '@/shared/ui';
import { SurveyChoiceField, SurveyTextField, clean } from './SurveyFields';
import { viaLabels } from './surveyUi';

export type AnswerTarget =
  { kind: 'RECEIVE'; revision: Revision } | { kind: 'CORRECT'; answer: Answer; revision: Revision };

interface AnswerFormValues {
  reason: string;
  received_at: string;
  received_via: ReceivedVia;
  values: { value: string }[];
}

const validReceivedTime = (value: number) =>
  Number.isFinite(value) && value >= Date.parse('2000-01-01T00:00:00Z') && value <= Date.now();

/** 版の設問ごとに回答を入力する。訂正は旧回答を取り下げるので ConfirmDialog を挟む。 */
export function SurveyAnswerForm({
  target,
  disabled,
  onSubmit,
}: {
  target: AnswerTarget;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
}) {
  const source = target.kind === 'CORRECT' ? target.answer : undefined;
  const received = source ? new Date(source.received_at) : null;
  const local = received
    ? new Date(received.getTime() - received.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
    : '';
  const form = useForm<AnswerFormValues>({
    defaultValues: {
      reason: '',
      received_at: local,
      received_via: source?.received_via ?? ('PAPER' as ReceivedVia),
      values: target.revision.questions.map(q => {
        const original = source?.answers.find(a => a.question_key === q.question_key);
        return { value: original?.text ?? original?.option_key ?? '' };
      }),
    },
  });
  const [confirmation, setConfirmation] = useState<Extract<
    SurveyCommand,
    { kind: 'CORRECT' }
  > | null>(null);
  const submit = form.handleSubmit(v => {
    const at = new Date(v.received_at).getTime();
    const answers = target.revision.questions.flatMap((q, index) => {
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
    if (target.kind === 'RECEIVE')
      void onSubmit({
        kind: 'RECEIVE',
        sid: target.revision.survey_id,
        rid: target.revision.id,
        input: { ...input, revision_version: target.revision.version },
      });
    else
      setConfirmation({
        kind: 'CORRECT',
        aid: target.answer.id,
        input: { ...input, reason: v.reason, version: target.answer.version },
      });
  });
  return (
    <>
      <Form {...form}>
        <form noValidate onSubmit={submit} className="space-y-6">
          <fieldset disabled={disabled} className="space-y-6">
            {target.revision.questions.map((q, index) =>
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
            {target.kind === 'CORRECT' && (
              <>
                <p>旧回答を取り下げ、同じ設問版に新しい回答を記録します。旧原文は残ります。</p>
                <SurveyTextField
                  control={form.control}
                  name="reason"
                  label="操作理由"
                  required
                  multiline
                  maxLength={500}
                  validate={v => clean(v, 500) || '操作理由を1〜500文字で入力してください'}
                />
              </>
            )}
            <Button type="submit" disabled={disabled}>
              {target.kind === 'RECEIVE' ? '回答を記録' : '確認へ'}
            </Button>
          </fieldset>
        </form>
      </Form>
      <ConfirmDialog
        open={confirmation !== null && !disabled}
        title="回答を訂正"
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
