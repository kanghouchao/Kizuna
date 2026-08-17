'use client';

import Link from 'next/link';
import { ChevronLeftIcon } from 'lucide-react';
import { useState } from 'react';
import { useParams } from 'next/navigation';
import { notify } from '@/shared/notify';
import { CustomerDuplicateResponse, customerApi } from '@/entities/customer';
import { getApiErrorMessage, storePath, useCursorList } from '@/shared/lib';
import { CustomerMergeComparison } from './CustomerMergeComparison';
import { CustomerMergeConfirmDialog } from './CustomerMergeConfirmDialog';
import {
  Badge,
  Button,
  Checkbox,
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 見比べる対象は 2 行。3 行以上を一度に畳む導線は持たない（ADR 0010 は一括統合を採らない）。 */
const PAIR_SIZE = 2;

/**
 * 選択中の 2 行。グループを跨いだ選択は持たない — 候補が意味を持つのは同じ番号のグループの中だけで、
 * 別グループの行と並べても「同じ番号だから疑わしい」という手がかりが消える。
 */
interface Selection {
  phoneNumber: string;
  ids: string[];
}

export default function CustomerDuplicatesPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const {
    rows: groups,
    isLoading,
    failed,
    hasMore,
    reload,
    loadMore,
  } = useCursorList(cursor => customerApi.duplicates({ cursor }));
  const [selection, setSelection] = useState<Selection | null>(null);
  const [survivingId, setSurvivingId] = useState<string | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const toggle = (phoneNumber: string, customerId: string) => {
    // 残す行の選択は選び直しのたびに捨てる。前の組み合わせで選んだ行が、次の組み合わせに
    // 残っていない状態で「残す行」として効いてしまわないように
    setSurvivingId(null);
    setSelection(current => {
      if (current === null || current.phoneNumber !== phoneNumber) {
        return { phoneNumber, ids: [customerId] };
      }
      if (current.ids.includes(customerId)) {
        return { ...current, ids: current.ids.filter(id => id !== customerId) };
      }
      if (current.ids.length >= PAIR_SIZE) return current;
      return { ...current, ids: [...current.ids, customerId] };
    });
  };

  const isSelected = (phoneNumber: string, customerId: string) =>
    selection?.phoneNumber === phoneNumber && selection.ids.includes(customerId);

  /** 選択中の 2 行を、候補一覧に並んでいる順のまま取り出す。 */
  const selectedPair = (
    rows: CustomerDuplicateResponse[]
  ): [CustomerDuplicateResponse, CustomerDuplicateResponse] | null => {
    const selected = rows.filter(row => row.id !== undefined && selection?.ids.includes(row.id));
    return selected.length === PAIR_SIZE ? [selected[0], selected[1]] : null;
  };

  const findSelected = (customerId: string | null) =>
    groups
      .flatMap(group => group.customers)
      .find(row => customerId !== null && row.id === customerId);

  const surviving = findSelected(survivingId);
  const merged = findSelected(selection?.ids.find(id => id !== survivingId) ?? null);

  const handleMerge = async () => {
    if (!surviving?.id || !merged?.id) return;
    try {
      setIsSubmitting(true);
      await customerApi.merge(surviving.id, merged.id);
      notify.success('顧客を統合しました');
      setIsConfirming(false);
      setSelection(null);
      setSurvivingId(null);
      // 畳んだ番号は候補から落ちる。取り直さないと、消えたはずの行が並んだままになる
      // （続きを読んでいても先頭から取り直す — 前の位置は畳んだ後の並びでは別の場所を指す）
      reload();
    } catch (error) {
      // 両行が会員に認領されている 409 は「先に関連を解除する」と読める文言をサーバが返す。
      // 汎用文言に潰すと、次の一手が画面から判らなくなる
      notify.error(getApiErrorMessage(error, '顧客の統合に失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">重複候補</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            電話番号が同じ顧客を手がかりとして並べています。同一人物かどうかはご自身で確かめてください。
          </p>
        </div>
        <Button render={<Link href={storePath(storeId, '/customers')} />} variant="outline">
          <ChevronLeftIcon />
          顧客一覧へ
        </Button>
      </div>

      <TableCard>
        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : failed ? (
          // 読めなかった候補を空表示にすると「重複は無い」と嘘をつくことになる
          <RegionError
            message="重複候補の取得に失敗しました"
            onRetry={reload}
            className="justify-center p-8"
          />
        ) : groups.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">
            電話番号が重複している顧客はいません
          </div>
        ) : (
          groups.map(group => {
            const phoneNumber = group.phone_number ?? '';
            const pair = selectedPair(group.customers);
            return (
              <div key={phoneNumber} className="border-b last:border-b-0">
                <div className="bg-muted/50 px-6 py-3">
                  <span className="text-sm text-muted-foreground">電話番号</span>{' '}
                  <span className="font-medium text-foreground">{phoneNumber}</span>
                  <span className="ml-2 text-sm text-muted-foreground">
                    {group.customers.length} 件
                  </span>
                </div>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-24">見比べる</TableHead>
                      <TableHead>名前</TableHead>
                      <TableHead>ランク</TableHead>
                      <TableHead>区分</TableHead>
                      <TableHead>受注</TableHead>
                      <TableHead>会員</TableHead>
                      <TableHead>NG</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {group.customers.map(row => (
                      <TableRow key={row.id}>
                        <TableCell>
                          {/* flex の容器が要る。Checkbox の既定の描画要素は span で、素の
                              テーブルセルに置くと display:inline のまま size-4 が効かず 2px に潰れる
                              （既存の呼出は FormItem の flex がこれを担っていた） */}
                          <div className="flex items-center">
                            {/* 名前を含む aria-label を持たせる。同じ画面に同型の選択が並ぶので、
                                「見比べる」だけでは読み上げでどの行か判らない */}
                            <Checkbox
                              aria-label={`${row.name} を見比べる`}
                              checked={isSelected(phoneNumber, row.id ?? '')}
                              // 3 行目以降は組み合わせが決まらないので、2 行選んだ時点で塞ぐ
                              disabled={
                                isSubmitting ||
                                (!isSelected(phoneNumber, row.id ?? '') &&
                                  selection?.phoneNumber === phoneNumber &&
                                  selection.ids.length >= PAIR_SIZE)
                              }
                              onCheckedChange={() => toggle(phoneNumber, row.id ?? '')}
                            />
                          </div>
                        </TableCell>
                        <TableCell className="font-medium text-foreground">{row.name}</TableCell>
                        <TableCell className="text-muted-foreground">{row.rank || '-'}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {row.classification || '-'}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {row.order_count} 件
                        </TableCell>
                        <TableCell>
                          {row.member_linked ? (
                            <Badge
                              variant="outline"
                              className="border-transparent bg-success/10 text-success-strong"
                            >
                              紐づけ済み
                            </Badge>
                          ) : (
                            <span className="text-muted-foreground">未紐づけ</span>
                          )}
                        </TableCell>
                        <TableCell>
                          {row.ng_type ? (
                            <Badge
                              variant="outline"
                              className="border-transparent bg-destructive/10 text-destructive-strong"
                            >
                              {row.ng_type}
                            </Badge>
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                {pair && (
                  <CustomerMergeComparison
                    rows={pair}
                    survivingId={survivingId}
                    onSurvivingChange={setSurvivingId}
                    onMerge={() => setIsConfirming(true)}
                    disabled={isSubmitting}
                  />
                )}
              </div>
            );
          })
        )}
        {hasMore && !isLoading && (
          <div className="flex justify-center border-t p-4">
            <Button variant="outline" onClick={loadMore}>
              さらに読み込む
            </Button>
          </div>
        )}
      </TableCard>

      {/* 確認は候補の取り直しで消えないよう外殻の外に置く */}
      <CustomerMergeConfirmDialog
        open={isConfirming && surviving !== undefined && merged !== undefined}
        survivingName={surviving?.name ?? ''}
        mergedName={merged?.name ?? ''}
        movedOrderCount={merged?.order_count ?? 0}
        isSubmitting={isSubmitting}
        onConfirm={() => void handleMerge()}
        onClose={() => setIsConfirming(false)}
      />
    </div>
  );
}
