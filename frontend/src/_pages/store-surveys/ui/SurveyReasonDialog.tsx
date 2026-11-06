import { useForm } from 'react-hook-form';
import { Answer, Revision, SurveyCommand } from '@/entities/survey';
import { Button, Dialog, DialogFrame, Form } from '@/shared/ui';
import { clean } from './SurveyEditor';
import { SurveyTextField } from './SurveyFields';

export type ReasonTask =
  { kind: 'OPEN' | 'CLOSE'; revision: Revision } | { kind: 'WITHDRAW'; answer: Answer };

// 終了と取り下げは元に戻せないので、確定を破壊操作の見た目にする
const COPY: Record<
  ReasonTask['kind'],
  { title: string; description: string; destructive?: boolean }
> = {
  OPEN: {
    title: '受付を開始',
    description: '開始後は設問の文面を変更できません。',
  },
  CLOSE: {
    title: '受付を終了',
    destructive: true,
    description: '終了後はこの版へ新しい回答を受け付けません。記録済みの回答は訂正できます。',
  },
  WITHDRAW: {
    title: '回答を取り下げ',
    destructive: true,
    description: '取り下げ後は復帰できません。原文と履歴は残り、個人情報の消去ではありません。',
  },
};

function commandOf(task: ReasonTask, reason: string): SurveyCommand {
  if (task.kind === 'WITHDRAW')
    return {
      kind: 'WITHDRAW',
      aid: task.answer.id,
      input: { version: task.answer.version, reason },
    };
  return {
    kind: task.kind,
    sid: task.revision.survey_id,
    rid: task.revision.id,
    input: { version: task.revision.version, reason },
  };
}

/**
 * 操作理由だけを入力して確定する短い操作。理由は履歴に残る必須の値なので ConfirmDialog では足りない。
 * form は閉じても残り、結果不明から開き直したときに入力を保つ。
 */
export function SurveyReasonDialog({
  task,
  open,
  onOpenChange,
  disabled,
  onSubmit,
  feedback,
}: {
  task: ReasonTask;
  open: boolean;
  onOpenChange: (value: boolean) => void;
  disabled: boolean;
  onSubmit: (command: SurveyCommand) => Promise<void>;
  feedback: React.ReactNode;
}) {
  const form = useForm<{ reason: string }>({ defaultValues: { reason: '' } });
  const copy = COPY[task.kind];
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <Form {...form}>
        <DialogFrame
          size="sm"
          title={copy.title}
          description={copy.description}
          form={{
            noValidate: true,
            onSubmit: form.handleSubmit(({ reason }) => void onSubmit(commandOf(task, reason))),
          }}
          footer={
            <>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                キャンセル
              </Button>
              <Button
                type="submit"
                variant={copy.destructive ? 'destructive' : 'default'}
                disabled={disabled}
              >
                確定する
              </Button>
            </>
          }
        >
          {feedback}
          <fieldset disabled={disabled}>
            <SurveyTextField
              control={form.control}
              name="reason"
              label="操作理由"
              required
              multiline
              maxLength={500}
              validate={v => clean(v, 500) || '操作理由を1〜500文字で入力してください'}
            />
          </fieldset>
        </DialogFrame>
      </Form>
    </Dialog>
  );
}
