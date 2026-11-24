'use client';

import { orderConflictField, StaleOrderError, useOrderConfirmation } from './useOrderConfirmation';
import { INPUT_KEPT_MESSAGE, LATEST_LOADED_MESSAGE, STALE_MESSAGE } from './orderStaleMessages';

import { OrderCourseField } from './OrderCourseField';
import { OrderSpecialServicesField } from './OrderSpecialServicesField';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { MePending, hasPermission, useCurrentStoreId, useMe } from '@/entities/user';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Order,
  OrderCorrectionResult,
  OrderFeeLineInput,
  orderApi,
  storeEditableFeeLines,
  readOnlyFeeLines,
  toFeeLineInputs,
} from '@/entities/order';
import { ExternalLinkIcon } from 'lucide-react';
import {
  getApiErrorMessage,
  storePath,
  useKeyedResource,
  useResourceInitialization,
  useVersionedWrite,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { customerHeadingText } from '../lib/customerLabel';
import { reseedForm } from '../lib/reseedForm';
// 空欄は「値なし」として送る。この契約は全量送信なので、省略と空欄が同じ意味になる。
import { optionalTime, toTimeInput } from '../lib/formValues';
import { OrderFeeLinesField } from './OrderFeeLinesField';
import {
  Button,
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  Input,
  RegionError,
  Textarea,
  VersionConflictNotice,
} from '@/shared/ui';
import { DeniedPage } from '@/widgets/admin-shell';

interface OrderCorrectionFormValues {
  actual_arrival_time: string;
  actual_end_time: string;
  special_service_revision_ids: string[];
  course_revision_id: string;
  fee_lines: OrderFeeLineInput[];
  reason: string;
}

const EMPTY_VALUES: OrderCorrectionFormValues = {
  actual_arrival_time: '',
  actual_end_time: '',
  special_service_revision_ids: [],
  course_revision_id: '',
  fee_lines: [],
  reason: '',
};

/** 訂正の結果と、その受注が現に帰属している会員（帰属していなければ null）。 */
interface CorrectionOutcomeState {
  result: OrderCorrectionResult;
  memberCode: string | null;
}

/**
 * 費用・報酬と原日時を示し、ポイントが動かないことを伝える。
 * 既往の手動調整との対応を追えないため、調整額を提案せず対象会員の台帳へ案内する。
 */
function CorrectionOutcome({ outcome }: { outcome: CorrectionOutcomeState }) {
  const { result, memberCode } = outcome;
  return (
    <div className="bg-card space-y-3 rounded-xl border p-4">
      <h2 className="text-foreground text-sm font-medium">訂正しました</h2>
      <p className="text-muted-foreground text-sm">
        請求 ¥{(result.previous_total_fee ?? 0).toLocaleString()} → ¥
        {(result.total_fee ?? 0).toLocaleString()}
      </p>
      <p>
        発生済み報酬 ¥{result.previous_accrued_remuneration.toLocaleString()} → ¥
        {result.accrued_remuneration.toLocaleString()}
      </p>
      <p>
        原営業日 {result.business_date} / 原完了日時{' '}
        {new Date(result.completed_at).toLocaleString('ja-JP')}
      </p>
      <p>
        訂正日時 {new Date(result.corrected_at).toLocaleString('ja-JP')} / 訂正 ID{' '}
        {result.correction_id}
      </p>
      {memberCode === null ? (
        <p className="text-muted-foreground text-sm">
          この受注は会員に帰属していないため、訂正で動くポイントはありません。伝票の申領が後から
          起きた場合、付与されるのは完了時点の会計に基づく額です。
        </p>
      ) : (
        <p className="text-muted-foreground text-sm">
          付与は完了時点の合計に基づく事実なので、この訂正では動きません。この受注は会員コード{' '}
          {memberCode} へ帰属しています。手当ての要否は会員ポイントの台帳で確かめてください。
        </p>
      )}
    </div>
  );
}

/**
 * 完了した受注の提供事実を理由付きで訂正する専用門。
 * 実績時刻の省略は値なし、編集可能明細は全量で保存する。凍結項目の欄は持たない。
 */
export default function OrderCorrectionPage() {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (!hasPermission(me, 'ORDER_MANAGE') || !hasPermission(me, 'ORDER_CORRECT'))
    return <DeniedPage />;
  return <CorrectionEditor />;
}

function CorrectionEditor() {
  const confirmation = useOrderConfirmation();
  const params = useParams();
  const storeId = useCurrentStoreId();
  const orderId = params.id as string;
  const router = useRouter();

  const resource = useKeyedResource<Order>(['order', storeId, orderId], () =>
    orderApi.get(orderId)
  );
  const { data: current, isLoading, failure, reload } = resource;
  const scope = `${storeId}:${orderId}`;
  const writer = useVersionedWrite(reload);
  const { busy } = writer;
  // 次の播種で操作者の入力を残す対象。播種で必ず消し、対象を名乗らせるので別の受注へは漏れない。
  // 残す取り直しの間はフォームを外さない。外すと、欄が自分で持つ選択中の表示名が元に戻る
  const [keepInputFor, setKeepInputFor] = useState<string | null>(null);
  const keepingInput = keepInputFor === scope;
  // 版が食い違った受注。入力は操作者が読み込みを選ぶまで残し、その間は訂正を塞ぐ
  const [staleFor, setStaleFor] = useState<string | null>(null);
  const stale = staleFor === scope;
  // 取り直しを始める操作はここを通し、入力を残すか置き換えるかを決める。
  // 取得失敗の再試行は直前の決定を引き継ぐ（入力を残す取り直しの失敗で入力を失わない）
  const reloadOrder = (keep: boolean) => {
    setKeepInputFor(keep ? scope : null);
    return writer.reload();
  };

  const form = useForm<OrderCorrectionFormValues>({ defaultValues: EMPTY_VALUES });
  const { handleSubmit, control } = form;
  const [outcomeState, setOutcome] = useState<
    (CorrectionOutcomeState & { orderId: string; storeId: string | undefined }) | null
  >(null);
  const outcome =
    outcomeState?.orderId === orderId && outcomeState.storeId === storeId ? outcomeState : null;

  // 巻き戻しは POINT_ADJUST（店長）限定。押せない導線を描くと、理由を入力し終えてから 403 を受け取る。
  // 強制はサーバ側 @PreAuthorize で、ここは導線の表示制御のみ。
  const canRollback = hasPermission(useMe().me, 'POINT_ADJUST');

  // 取得の到着はレンダーより後なので、初期値ではなく効果で播く（開いた最初のフレームが空欄になる）。
  const initialized = useResourceInitialization(resource.success, current => {
    reseedForm(
      form,
      {
        actual_arrival_time: toTimeInput(current.actual_arrival_time),
        actual_end_time: toTimeInput(current.actual_end_time),
        special_service_revision_ids: (current.special_services ?? []).map(
          item => item.revision_id
        ),
        course_revision_id: '',
        fee_lines: storeEditableFeeLines(current.fee_lines),
        reason: '',
      },
      keepingInput
    );
    setKeepInputFor(null);
    if (!keepingInput) setStaleFor(null);
  });
  const seeded = current !== null && (keepingInput || (initialized && !isLoading));
  const completed = current?.status === 'COMPLETED' && !current.completion_invalidated;

  /**
   * この受注が現に帰属している会員のコード。帰属していなければ null。
   *
   * 手当ての宛先は帰属記録であって、顧客に今紐づく会員ではない（ADR 0012）。読めなかったときは
   * 帰属している側へ倒す — 「動くポイントはありません」と誤って言い切ると、実際に残った誤付与に
   * 気づく機会がどこにも無くなる。宛先が名乗れないぶんは不明として示す。
   */
  const attributedMemberCode = async (): Promise<string | null> => {
    try {
      const attribution = await orderApi.attribution(orderId);
      return attribution.attributed ? (attribution.member_code ?? '不明') : null;
    } catch {
      return '不明';
    }
  };

  // 通知は消えるので、画面にも止めている理由を残す（押せなくなった操作のそばの案内）
  const markStale = () => {
    setStaleFor(scope);
    notify.warning(STALE_MESSAGE);
  };
  const loadLatest = async () => {
    const result = await reloadOrder(false);
    if (result.status === 'success' && result.isCurrent()) notify.success(LATEST_LOADED_MESSAGE);
  };

  const submit = async (values: OrderCorrectionFormValues) => {
    if (current === null || current.version === undefined) {
      return;
    }
    const operation = resource.capture();
    const request = {
      // 開いた時点の版をそのまま返す。全量を送る口なので、間に別の操作者の訂正が挟まっていれば
      // サーバが 409 で差し戻す（読み直さずに送ると、その訂正を黙って巻き戻す）
      expected_version: current.version,
      reason: values.reason.trim(),
      actual_arrival_time: optionalTime(values.actual_arrival_time),
      actual_end_time: optionalTime(values.actual_end_time),
      special_service_revision_ids: values.special_service_revision_ids,
      course_revision_id: values.course_revision_id || undefined,
      fee_lines: toFeeLineInputs(values.fee_lines),
    };
    let token: string | null;
    try {
      token = await confirmation.confirm(() => orderApi.previewCorrection(orderId, request));
    } catch (error) {
      // 試算が版の食い違いで止まったときだけここへ来る
      if (error instanceof StaleOrderError && error.reloadNow) await loadLatest();
      else markStale();
      return;
    }
    if (!token) return;
    let conflictField: string | undefined;
    const outcome = await writer.write(
      () => orderApi.correct(orderId, { ...request, confirmation_token: token }),
      {
        failure: (error, kind) => {
          if (kind === 'conflict') conflictField = orderConflictField(error);
          if (!operation.isCurrent()) return;
          if (conflictField === 'confirmation_token') {
            notify.warning(
              '採用条件が変更されたため訂正できませんでした。入力を残したまま最新の状態を取得します'
            );
          } else if (conflictField !== 'expected_version') {
            notify.error(getApiErrorMessage(error, '受注の訂正に失敗しました'));
          }
        },
        onSuccess: corrected => {
          if (!operation.isCurrent()) return;
          notify.success('受注を訂正しました');
          setOutcome({ result: corrected, memberCode: '不明', orderId, storeId });
          void reloadOrder(false);
          // 補助情報の遅延で訂正結果を隠さない。詳細再取得後の世代に結び付け、旧対象へは反映しない。
          const enrichment = resource.capture();
          void attributedMemberCode().then(memberCode => {
            if (!enrichment.isCurrent()) return;
            setOutcome(outcome =>
              outcome?.result === corrected ? { ...outcome, memberCode } : outcome
            );
          });
        },
      }
    );
    if (outcome !== 'conflict') return;
    // 版の食い違いは操作者の読み込みを待つ。それ以外の 409 は入力を残したまま版を取り直す
    if (conflictField === 'expected_version') {
      markStale();
      return;
    }
    const result = await reloadOrder(true);
    if (result.status === 'success' && result.isCurrent()) notify.success(INPUT_KEPT_MESSAGE);
  };

  return (
    <>
      {confirmation.dialog}
      <div className="space-y-6">
        <div>
          <h1 className="text-foreground text-2xl font-bold">完了後の訂正</h1>
          {current !== null && (
            <p className="text-muted-foreground mt-1 text-sm">
              {customerHeadingText(current)}
              {current.business_date ? ` ・ ${current.business_date}` : ''}
            </p>
          )}
        </div>

        {isLoading && <p className="text-muted-foreground text-sm">読み込み中...</p>}
        {failure === 'error' && (
          <RegionError
            message="受注を取得できませんでした。"
            onRetry={() => void writer.reload()}
          />
        )}
        {failure === 'notFound' && (
          <RegionError
            message="この受注は見つかりませんでした。"
            fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
          />
        )}

        {current?.completion_invalidated && (
          <p role="alert">
            無効化した受注は訂正できません。再提供は関連する新受注で記録してください。
          </p>
        )}
        {outcome !== null && <CorrectionOutcome outcome={outcome} />}

        {/* 門は台帳を読みも書きもしない（ADR 0019）。全否定が要る誤完了のためにポイント側の操作面を
          指すだけで、機構は繋がない — 門はここが実行されたかを知らないまま */}
        {seeded && completed && (
          <div className="bg-card flex flex-wrap items-center justify-between gap-3 rounded-xl border p-4">
            <p className="text-muted-foreground text-sm">
              この訂正でポイントは動きません。利用の返還は専用のポイント巻き戻しで行います。
              {!canRollback && 'POINT_ADJUST を持つポイント救済担当者へ依頼してください。'}
            </p>
            <Button
              variant="outline"
              size="sm"
              render={<Link href={storePath(storeId, `/orders/${orderId}/point-rollback`)} />}
            >
              <ExternalLinkIcon aria-hidden="true" />
              ポイントの巻き戻しへ
            </Button>
          </div>
        )}

        {/* 完了していない受注はサーバが撥ねる。欄を出してから 400 を返すより、開いた時点で理由を名乗る */}
        {seeded && !completed && (
          <RegionError
            message="完了した受注だけが訂正できます。未完了の受注は編集画面から、取消済みの受注は同じ内容で起こし直してください。"
            fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
          />
        )}

        {seeded && completed && (
          <Form {...form}>
            <form onSubmit={handleSubmit(submit)} className="space-y-6">
              <section className="space-y-3">
                <h2 className="text-muted-foreground text-sm font-medium">実績</h2>
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={control}
                    name="actual_arrival_time"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>実際の到着</FormLabel>
                        <FormControl>
                          <Input type="time" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={control}
                    name="actual_end_time"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>実際の終了</FormLabel>
                        <FormControl>
                          <Input type="time" {...field} />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>
              </section>

              <section className="space-y-3">
                <h2 className="text-muted-foreground text-sm font-medium">コース</h2>
                <div className="space-y-4">
                  <OrderCourseField historicalOrderId={orderId} current={current.course} />
                  <OrderSpecialServicesField
                    historicalOrderId={orderId}
                    current={current.special_services}
                  />
                </div>
              </section>

              <section className="space-y-3">
                <h2 className="text-muted-foreground text-sm font-medium">会計</h2>
                {/* ポイント利用の行は門内でも編集不可。読み取りで並べるだけ（誤りはポイント機構で直す） */}
                <OrderFeeLinesField
                  historicalOrderId={current.id}
                  systemLines={readOnlyFeeLines(current.fee_lines)}
                />
              </section>

              <section className="space-y-3">
                <h2 className="text-muted-foreground text-sm font-medium">訂正の理由</h2>
                <FormField
                  control={control}
                  name="reason"
                  rules={{
                    validate: value => value.trim() !== '' || '訂正の理由を入力してください',
                    maxLength: {
                      value: 500,
                      message: '訂正の理由は 500 文字以内で入力してください',
                    },
                  }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>理由</FormLabel>
                      <FormControl>
                        <Textarea rows={3} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <p className="text-muted-foreground text-xs">
                  確定した記録を動かす操作なので、理由・実行者・時刻と訂正前の内容が履歴に残ります。
                </p>
              </section>

              {stale && <VersionConflictNotice busy={busy} onLoad={() => void loadLatest()} />}
              <div className="flex justify-end gap-4">
                <Button
                  type="button"
                  variant="outline"
                  disabled={busy}
                  onClick={() => router.push(storePath(storeId, '/orders'))}
                >
                  一覧へ戻る
                </Button>
                <Button type="submit" disabled={busy || writer.conflict || stale}>
                  {busy ? '訂正中...' : '訂正する'}
                </Button>
              </div>
            </form>
          </Form>
        )}
      </div>
    </>
  );
}
