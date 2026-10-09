'use client';
import { useState } from 'react';
import { remunerationApi } from '../api';
import { hasPermission, readTokenClaims, useResource } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { RemunerationEditor } from './RemunerationEditor';
import {
  currentOperationScope,
  usePendingOperation,
  type EditTarget,
  type Operation,
} from '../model/operation';
import { RemunerationHistory } from './RemunerationHistory';
import { Paging } from './Paging';
export function RemunerationManagement({
  personId,
  personName,
  month,
  refreshKey,
  onSaved,
}: {
  personId: number;
  personName: string;
  month: string;
  refreshKey: number;
  onSaved: () => void;
}) {
  const [guaranteePage, setGuaranteePage] = useState(0),
    [bonusPage, setBonusPage] = useState(0),
    [revision, setRevision] = useState(0);
  const scope = currentOperationScope();
  const outstanding = usePendingOperation(scope);
  const blocked = !scope || outstanding !== null;
  const [editor, setEditor] = useState<{
    scope: string;
    target: EditTarget;
    personId: number;
    personName: string;
    recovery?: Operation;
    open: boolean;
    key: number;
  } | null>(null);
  const edit = (target: EditTarget) => {
    if (blocked) return;
    setEditor(current => ({
      scope: scope!,
      target,
      personId,
      personName,
      open: true,
      key: (current?.key ?? 0) + 1,
    }));
  };
  const restore = () => {
    if (!outstanding) return;
    const operation = outstanding.operation;
    setEditor(current => ({
      scope: operation.scope,
      target: operation.target,
      personId: operation.personId,
      personName: operation.personName,
      recovery: operation,
      open: true,
      key: (current?.key ?? 0) + 1,
    }));
  };
  const [history, setHistory] = useState<{
    kind: 'guarantee' | 'bonus';
    id: string;
    open: boolean;
    key: number;
  } | null>(null);
  const showHistory = (kind: 'guarantee' | 'bonus', id: string) =>
    setHistory(current => ({ kind, id, open: true, key: (current?.key ?? 0) + 1 }));
  const [claims] = useState(readTokenClaims);
  const canGuarantee = hasPermission(claims, 'GUARANTEE_MANAGE'),
    canBonus = hasPermission(claims, 'BONUS_AWARD'),
    canCorrect = hasPermission(claims, 'REMUNERATION_CORRECT');
  const guarantees = useResource(
    () => remunerationApi.guarantees(personId, guaranteePage),
    [personId, guaranteePage, revision, refreshKey]
  );
  const bonuses = useResource(
    () => remunerationApi.bonuses(personId, month, bonusPage),
    [personId, month, bonusPage, revision, refreshKey]
  );
  const g = !guarantees.isLoading && guarantees.failure === null ? guarantees.data : null;
  const b = !bonuses.isLoading && bonuses.failure === null ? bonuses.data : null;
  return (
    <section className="space-y-6 border-t pt-6" aria-label="保証とボーナスの管理">
      <h3 className="font-semibold">保証条件・付与記録の管理</h3>
      {outstanding && (
        <div role="status" className="space-y-3 rounded-lg border p-4">
          <p>
            {outstanding.operation.personName}{' '}
            の送信結果を確認するまで、新しい記録は作成できません。
          </p>
          <Button
            type="button"
            variant="outline"
            disabled={outstanding.phase === 'submitting'}
            onClick={restore}
          >
            未確認の送信を復元
          </Button>
        </div>
      )}
      {guarantees.isLoading && <p role="status">保証条件を読み込み中...</p>}
      {guarantees.failure !== null && (
        <RegionError message="保証条件を取得できませんでした。" onRetry={guarantees.reload} />
      )}
      {g && (
        <div className="space-y-3">
          {canGuarantee && (
            <Button
              type="button"
              disabled={blocked}
              onClick={() => edit({ kind: 'guarantee', version: g.version })}
            >
              保証条件を追加・停止
            </Button>
          )}
          {g.entries.content.length === 0 && <p>日額保証は未設定です。</p>}
          {g.entries.content.map(item => (
            <article className="space-y-3 rounded-lg border p-4" key={item.id}>
              <p>
                {item.effective_from}〜{' '}
                {item.state === 'STOPPED'
                  ? '停止'
                  : `日額 ¥${item.daily_amount?.toLocaleString('ja-JP')}`}{' '}
                {item.cancelled_at ? '（取消済み）' : ''}
              </p>
              <p>{item.reason}</p>
              <div className="flex flex-wrap gap-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => showHistory('guarantee', item.id)}
                >
                  保証の変更履歴
                </Button>
                {canGuarantee && canCorrect && !item.cancelled_at && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={blocked}
                      onClick={() => edit({ kind: 'guarantee', item, version: g.version })}
                    >
                      保証条件を訂正
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={blocked}
                      onClick={() =>
                        edit({ kind: 'guarantee', item, version: g.version, cancel: true })
                      }
                    >
                      保証条件を取り消す
                    </Button>
                  </>
                )}
              </div>
            </article>
          ))}
          <Paging
            page={guaranteePage}
            total={g.entries.total_pages}
            label="保証条件"
            onPage={setGuaranteePage}
          />
        </div>
      )}
      {canBonus && (
        <Button type="button" disabled={blocked} onClick={() => edit({ kind: 'bonus' })}>
          ボーナスを記録
        </Button>
      )}
      {bonuses.isLoading && <p role="status">ボーナスを読み込み中...</p>}
      {bonuses.failure !== null && (
        <RegionError message="ボーナスを取得できませんでした。" onRetry={bonuses.reload} />
      )}
      {b && (
        <div className="space-y-3">
          {b.content.map(item => (
            <article className="space-y-3 rounded-lg border p-4" key={item.id}>
              <p>
                {item.award_date} ¥{item.effective_amount.toLocaleString('ja-JP')}{' '}
                {item.cancelled_at ? '（取消済み）' : ''}
              </p>
              <p>{item.reason}</p>
              <div className="flex flex-wrap gap-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => showHistory('bonus', item.id)}
                >
                  ボーナスの変更履歴
                </Button>
                {canBonus && canCorrect && !item.cancelled_at && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={blocked}
                      onClick={() => edit({ kind: 'bonus', item })}
                    >
                      ボーナスを訂正
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      disabled={blocked}
                      onClick={() => edit({ kind: 'bonus', item, cancel: true })}
                    >
                      ボーナスを取り消す
                    </Button>
                  </>
                )}
              </div>
            </article>
          ))}
          <Paging page={bonusPage} total={b.total_pages} label="付与記録" onPage={setBonusPage} />
        </div>
      )}
      {editor && scope && editor.scope === scope && (
        <RemunerationEditor
          key={editor.key}
          target={editor.target}
          open={editor.open}
          personId={editor.personId}
          personName={editor.personName}
          scope={scope}
          recovery={editor.recovery}
          onClose={() => setEditor(current => current && { ...current, open: false })}
          onSaved={() => {
            setRevision(v => v + 1);
            onSaved();
          }}
        />
      )}
      {history && (
        <RemunerationHistory
          key={history.key}
          kind={history.kind}
          id={history.id}
          open={history.open}
          onClose={() => setHistory(current => current && { ...current, open: false })}
        />
      )}
    </section>
  );
}
