import { useForm, useFieldArray, Control, useWatch, useFormContext } from 'react-hook-form';
import { DefinitionInput, Question } from '@/entities/survey';
import {
  Button,
  Checkbox,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/shared/ui';
import { createUuid } from '@/shared/lib';
import { SurveyTextField, SurveyChoiceField, clean } from './SurveyFields';

const blankQuestion = (): Question => ({
  question_key: `q${createUuid()}`,
  type: 'TEXT',
  prompt: '',
  required: true,
  options: [],
});

/** 設問と選択肢の入れ子を編集する。初期値は開いた時点の版の内容で、以後は入力が正になる。 */
export function SurveyDefinitionForm({
  initial,
  disabled,
  onSubmit,
}: {
  initial?: DefinitionInput;
  disabled: boolean;
  onSubmit: (input: DefinitionInput) => void;
}) {
  const form = useForm<DefinitionInput>({
    defaultValues: initial ?? { title: '', questions: [blankQuestion()] },
  });
  const questions = useFieldArray({ control: form.control, name: 'questions' });
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="space-y-6">
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
                  { option_key: `o${createUuid()}`, label: '' },
                  { option_key: `o${createUuid()}`, label: '' },
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
            onClick={() => options.append({ option_key: `o${createUuid()}`, label: '' })}
          >
            選択肢を追加
          </Button>
        </div>
      )}
    </>
  );
}
