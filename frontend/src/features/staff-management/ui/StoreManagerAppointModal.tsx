'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { StoreManagerCandidateResponse, storeManagerApi } from '@/entities/user';
import {
  EMAIL_PATTERN,
  EMAIL_PATTERN_MESSAGE,
  getApiErrorMessage,
  useListPage,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/shared/ui';

/** 候補一覧 1 ページあたりの件数。選ぶための一覧なので短く保つ。 */
const PAGE_SIZE = 5;

const EXISTING_TAB = 'existing';
const NEW_TAB = 'new';

interface StoreManagerAppointModalProps {
  storeId: string;
  onClose: () => void;
  /** 任命成功後に呼ばれる（店長一覧の再取得用）。 */
  onAppointed: () => void;
}

/** 値はそのまま任命リクエストになるため、欄名は wire のキーに合わせる。 */
interface NewManagerFormValues {
  email: string;
  password: string;
  display_name: string;
}

/**
 * 店長の任命モーダル。既存アカウントの選択と、初代店長のための新規作成を並べる。
 *
 * 候補の母集団（HQ 側ロール保持者・全店舗担当・停止中・既に本店の店長を外す）はサーバが決める。
 * 前端は返ってきた集合をそのまま並べ、「任命できるとは何か」の判定を複製しない。
 */
export function StoreManagerAppointModal({
  storeId,
  onClose,
  onAppointed,
}: StoreManagerAppointModalProps) {
  const [tab, setTab] = useState(EXISTING_TAB);
  const [searchTerm, setSearchTerm] = useState('');
  // 選択中の候補ではなく送信中の候補を持つ。行ごとにボタンを置くので、押した行だけを塞げばよい。
  const [appointingId, setAppointingId] = useState<number | null>(null);

  const candidates = useListPage<StoreManagerCandidateResponse, string>(
    (page, search) =>
      storeManagerApi.candidates(storeId, {
        page,
        size: PAGE_SIZE,
        search: search || undefined,
      }),
    ''
  );

  const form = useForm<NewManagerFormValues>({
    defaultValues: { email: '', password: '', display_name: '' },
  });
  const {
    control,
    handleSubmit,
    formState: { isSubmitting },
  } = form;

  const busy = isSubmitting || appointingId !== null;

  const appointExisting = async (candidate: StoreManagerCandidateResponse) => {
    if (candidate.id === undefined) return;
    setAppointingId(candidate.id);
    try {
      await storeManagerApi.appoint(storeId, { user_id: candidate.id });
      notify.success('店長に任命しました');
      onAppointed();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '店長の任命に失敗しました'));
    } finally {
      setAppointingId(null);
    }
  };

  const createAndAppoint = async (values: NewManagerFormValues) => {
    try {
      await storeManagerApi.appoint(storeId, values);
      notify.success('店長を作成して任命しました');
      onAppointed();
      onClose();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '店長の任命に失敗しました'));
    }
  };

  return (
    <Dialog
      open
      onOpenChange={next => {
        // 送信中は閉じさせない。閉じると unmount で送信中の印が消え、開き直した複製から二重送信できる
        if (!next && !busy) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[calc(100vh-2rem)] gap-0 overflow-y-auto rounded-[10px] p-0 sm:max-w-md"
      >
        <DialogTitle className="border-b px-6 py-4 text-lg font-semibold text-foreground">
          店長を任命
        </DialogTitle>
        <Tabs value={tab} onValueChange={setTab} className="gap-0 px-6 py-5">
          <TabsList
            variant="line"
            className="h-auto w-full justify-start gap-6 rounded-none border-b p-0"
          >
            <TabsTrigger value={EXISTING_TAB} className="rounded-none px-0 pb-3">
              既存アカウント
            </TabsTrigger>
            <TabsTrigger value={NEW_TAB} className="rounded-none px-0 pb-3">
              新規作成
            </TabsTrigger>
          </TabsList>

          <TabsContent value={EXISTING_TAB} className="mt-5 space-y-4">
            <form
              onSubmit={event => {
                event.preventDefault();
                void candidates.search(searchTerm);
              }}
              className="flex gap-2"
            >
              <label htmlFor="manager-candidate-search" className="sr-only">
                任命候補を検索
              </label>
              <Input
                type="text"
                id="manager-candidate-search"
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                placeholder="氏名またはメールアドレスで検索..."
              />
              <Button type="submit" variant="outline">
                検索
              </Button>
            </form>

            {candidates.failed ? (
              <RegionError
                message="候補の取得に失敗しました"
                onRetry={() => void candidates.reload()}
              />
            ) : candidates.isLoading ? (
              <p className="py-6 text-center text-sm text-muted-foreground">読み込み中...</p>
            ) : candidates.rows.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                任命できるアカウントがありません
              </p>
            ) : (
              <ul className="divide-y rounded-lg border">
                {candidates.rows.map(candidate => (
                  <li
                    key={candidate.id}
                    className="flex items-center justify-between gap-3 px-4 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-foreground">
                        {candidate.display_name}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{candidate.email}</p>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      disabled={busy}
                      onClick={() => void appointExisting(candidate)}
                    >
                      {appointingId === candidate.id ? '任命中...' : '任命'}
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            {candidates.pageCount > 1 && (
              <div className="flex items-center justify-between text-sm">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={candidates.page === 0}
                  onClick={() => void candidates.onPageChange(candidates.page - 1)}
                >
                  前へ
                </Button>
                <span className="text-muted-foreground">
                  {candidates.page + 1} / {candidates.pageCount}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={candidates.page + 1 >= candidates.pageCount}
                  onClick={() => void candidates.onPageChange(candidates.page + 1)}
                >
                  次へ
                </Button>
              </div>
            )}
          </TabsContent>

          <TabsContent value={NEW_TAB} className="mt-5">
            <Form {...form}>
              {/* noValidate: 未達の原生制約が生きている限りブラウザが submit の手前で止め、
                  我々の文言は永久に描かれない。執行は下の各 rules が担う */}
              <form onSubmit={handleSubmit(createAndAppoint)} noValidate className="space-y-4">
                <FormField
                  control={control}
                  name="email"
                  rules={{
                    required: 'メールアドレスを入力してください',
                    pattern: { value: EMAIL_PATTERN, message: EMAIL_PATTERN_MESSAGE },
                  }}
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>メールアドレス</FormLabel>
                      <FormControl>
                        <Input type="email" maxLength={127} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="password"
                  rules={{ required: '初期パスワードを入力してください' }}
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>初期パスワード</FormLabel>
                      <FormControl>
                        <Input type="password" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={control}
                  name="display_name"
                  rules={{ required: '氏名を入力してください' }}
                  render={({ field }) => (
                    <FormItem className="gap-1">
                      <FormLabel>氏名</FormLabel>
                      <FormControl>
                        <Input type="text" maxLength={150} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <p className="text-xs text-muted-foreground">
                  この店舗だけを担当する店長として作成されます。
                </p>
                <div className="flex justify-end">
                  <Button type="submit" disabled={busy}>
                    {isSubmitting ? '作成中...' : '作成して任命'}
                  </Button>
                </div>
              </form>
            </Form>
          </TabsContent>
        </Tabs>
        <div className="flex justify-end border-t px-6 py-4">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>
            閉じる
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
