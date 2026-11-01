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
  ImageUpload,
  Input,
  Label,
  Textarea,
} from '@/shared/ui';

/** 画像の選択欄は未選択を空文字で持つので、送信の境界で null に戻す。 */
type ArticleFormValues = Omit<ArticleInput, 'cover_image_url'> & { cover_image_url: string };

const textRule = (max: number, message: string) => (value: string) =>
  (value.trim().length > 0 && value.length <= max && !value.includes('\0')) || message;

export function ArticleForm({
  initial,
  busy,
  submitLabel,
  onSubmit,
}: {
  initial?: { title: string; body: string; cover_image_url?: string | null };
  busy: boolean;
  submitLabel: string;
  onSubmit: (input: ArticleInput) => Promise<void>;
}) {
  const form = useForm<ArticleFormValues>({
    defaultValues: {
      title: initial?.title ?? '',
      body: initial?.body ?? '',
      cover_image_url: initial?.cover_image_url ?? '',
    },
  });
  return (
    <Form {...form}>
      <form
        className="space-y-6"
        noValidate
        onSubmit={form.handleSubmit(values =>
          onSubmit({ ...values, cover_image_url: values.cover_image_url || null })
        )}
      >
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
        <div className="grid gap-2">
          <Label>表紙画像（任意）</Label>
          <p className="text-xs text-muted-foreground">
            お知らせ一覧と記事ページに表示されます。横長の画像を推奨します。
          </p>
          <FormField
            control={form.control}
            name="cover_image_url"
            render={({ field: { value, onChange } }) => (
              <ImageUpload
                purpose="article-cover"
                value={value || undefined}
                onChange={onChange}
                className="w-full max-w-md aspect-video"
              />
            )}
          />
        </div>
        <Button type="submit" disabled={busy}>
          {submitLabel}
        </Button>
      </form>
    </Form>
  );
}
