'use client';

import { useState } from 'react';
import {
  BenefitRuleSummaryResponse,
  benefitRuleApi,
  benefitRuleRepeatPolicyLabel,
  benefitRuleTypeLabel,
} from '@/entities/benefit-rule';
import { PlatformStore, platformAuthApi } from '@/entities/user';
import { getApiErrorMessage, useListPage, useManagedList } from '@/shared/lib';
import { notify } from '@/shared/notify';
import { ListPage } from '@/widgets/list-page';
import {
  Badge,
  Button,
  ConfirmDialog,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { BenefitRuleFormModal } from './BenefitRuleFormModal';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 10;

/** 適用期間の表示。開始も終了も無い規則は常設。 */
function periodLabel(rule: BenefitRuleSummaryResponse): string {
  if (!rule.effective_from && !rule.effective_until) return '常設';
  return `${rule.effective_from ?? ''} 〜 ${rule.effective_until ?? ''}`;
}

/** 付与量の表示。紹介だけが紹介者・被紹介者の二値を持つ。 */
function pointsLabel(rule: BenefitRuleSummaryResponse): string {
  if (rule.type === 'REFERRAL') {
    return `紹介者 ${rule.referrer_points ?? 0}P / 被紹介者 ${rule.referred_points ?? 0}P`;
  }
  return `${rule.points ?? 0}P`;
}

function scopeLabel(rule: BenefitRuleSummaryResponse): string {
  return rule.store_scope_type === 'SPECIFIC_STORES' ? `${rule.store_count} 店舗` : '全店舗';
}

/**
 * 特典規則の管理ページ（一覧・作成・編集・停用）。削除の口は持たない — 付与仕訳が規則を
 * 指し返すため、退場は停用で表す。停用済みの規則も一覧に並ぶ。
 */
export default function BenefitRulesPage() {
  const list = useListPage<BenefitRuleSummaryResponse>(page =>
    benefitRuleApi.list({ page, size: PAGE_SIZE })
  );
  const rules = list.rows;

  // 適用店舗の選択肢。一覧が 1 回だけ取得し、モーダルへは props で渡す
  const {
    items: stores,
    isLoading: storesLoading,
    failed: storesFailed,
    refetch: refetchStores,
  } = useManagedList<PlatformStore>(() => platformAuthApi.stores());

  // null = 閉じている、'new' = 新規作成、数値 = その規則の編集
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [deactivating, setDeactivating] = useState<BenefitRuleSummaryResponse | null>(null);

  const deactivate = async (rule: BenefitRuleSummaryResponse) => {
    setDeactivating(null);
    try {
      await benefitRuleApi.deactivate(rule.id ?? 0);
      notify.success('特典規則を停用しました');
      void list.reload();
    } catch (error) {
      notify.error(getApiErrorMessage(error, '特典規則の停用に失敗しました'));
    }
  };

  return (
    <>
      <ListPage
        title="特典規則"
        description="紹介・ログイン・来店を契機とする追加ポイント付与の規則を管理します。"
        actions={
          <Button type="button" onClick={() => setEditing('new')}>
            規則を作成
          </Button>
        }
        state={list}
        emptyMessage="特典規則が登録されていません"
        errorMessage="特典規則一覧の取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>規則名</TableHead>
              <TableHead>種別</TableHead>
              <TableHead>適用店舗</TableHead>
              <TableHead>適用期間</TableHead>
              <TableHead>付与量</TableHead>
              <TableHead>重複可否</TableHead>
              <TableHead>状態</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rules.map(rule => (
              <TableRow key={rule.id}>
                <TableCell className="font-medium text-foreground">{rule.name}</TableCell>
                <TableCell className="text-muted-foreground">
                  {benefitRuleTypeLabel(rule.type)}
                </TableCell>
                <TableCell className="text-muted-foreground">{scopeLabel(rule)}</TableCell>
                <TableCell className="text-muted-foreground">{periodLabel(rule)}</TableCell>
                <TableCell className="text-muted-foreground">{pointsLabel(rule)}</TableCell>
                <TableCell className="text-muted-foreground">
                  {benefitRuleRepeatPolicyLabel(rule.repeat_policy)}
                </TableCell>
                <TableCell>
                  {rule.enabled ? (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      有効
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="border-transparent bg-warning/10 text-warning-strong"
                    >
                      停用済み
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  {/* 停用済みは退場した規則で、編集も停用も受け付けない */}
                  {rule.enabled && (
                    <>
                      <Button variant="ghost" size="sm" onClick={() => setEditing(rule.id ?? 0)}>
                        編集
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-destructive-strong"
                        onClick={() => setDeactivating(rule)}
                      >
                        停用
                      </Button>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>

      {editing !== null && (
        <BenefitRuleFormModal
          editingId={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => void list.reload()}
          stores={stores}
          storesLoading={storesLoading}
          storesFailed={storesFailed}
          onReloadStores={() => void refetchStores()}
        />
      )}

      <ConfirmDialog
        open={deactivating !== null}
        title="特典規則を停用しますか？"
        description={`${deactivating?.name ?? ''} は以後発火しなくなります。規則は削除されず一覧に残りますが、再開の口はありません（同じ内容で作り直してください）。`}
        confirmLabel="停用する"
        onConfirm={() => void (deactivating && deactivate(deactivating))}
        onClose={() => setDeactivating(null)}
      />
    </>
  );
}
