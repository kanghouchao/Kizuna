import { useForm } from 'react-hook-form';
import { ARTICLE_BODY_MAX, ARTICLE_TITLE_MAX, ArticleInput } from '@/entities/content/article';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  Textarea,
} from '@/shared/ui';

const textRule = (max: number, message: string) => (value: string) =>
  (value.trim().length > 0 && value.length <= max && !value.includes('\0')) || message;

export function ArticleForm({
  initial,
  busy,
  submitLabel,
  onSubmit,
}: {
  initial?: ArticleInput;
  busy: boolean;
  submitLabel: string;
  onSubmit: (input: ArticleInput) => Promise<void>;
}) {
  const form = useForm<ArticleInput>({
    defaultValues: { title: initial?.title ?? '', body: initial?.body ?? '' },
  });
  return (
    <Form {...form}>
      <form className="space-y-6" noValidate onSubmit={form.handleSubmit(onSubmit)}>
        <FormField
          control={form.control}
          name="title"
          rules={{
            validate: textRule(
              ARTICLE_TITLE_MAX,
              `表題を1〜${ARTICLE_TITLE_MAX}文字で入力してください`
            ),
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>表題</FormLabel>
              <FormControl>
                <Input {...field} required disabled={busy} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="body"
          rules={{
            validate: textRule(
              ARTICLE_BODY_MAX,
              `本文を1〜${ARTICLE_BODY_MAX}文字で入力してください`
            ),
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>本文</FormLabel>
              <FormControl>
                <Textarea {...field} required disabled={busy} rows={12} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" disabled={busy}>
          {submitLabel}
        </Button>
      </form>
    </Form>
  );
}
