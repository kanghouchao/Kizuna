'use client';
import { useState } from 'react';
import { remunerationApi } from '../api';
import { hasPermission, readTokenClaims, useResource } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { RemunerationEditor, type EditTarget } from './RemunerationEditor';
import { RemunerationHistory } from './RemunerationHistory';
import { Paging } from './Paging';
export function RemunerationManagement({
  personId,
  month,
  refreshKey,
  onSaved,
}: {
  personId: number;
  month: string;
  refreshKey: number;
  onSaved: () => void;
}) {
  const [guaranteePage, setGuaranteePage] = useState(0),
    [bonusPage, setBonusPage] = useState(0),
    [revision, setRevision] = useState(0);
  const [editor, setEditor] = useState<{ target: EditTarget; open: boolean; key: number } | null>(
    null
  );
  const edit = (target: EditTarget) =>
    setEditor(current => ({ target, open: true, key: (current?.key ?? 0) + 1 }));
  const [history, setHistory] = useState<{
    kind: 'guarantee' | 'bonus';
    id: string;
    open: boolean;
  } | null>(null);
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
      {guarantees.isLoading && <p role="status">保証条件を読み込み中...</p>}
      {guarantees.failure !== null && (
        <RegionError message="保証条件を取得できませんでした。" onRetry={guarantees.reload} />
      )}
      {g && (
        <div className="space-y-3">
          {canGuarantee && (
            <Button type="button" onClick={() => edit({ kind: 'guarantee', version: g.version })}>
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
                  onClick={() => setHistory({ kind: 'guarantee', id: item.id, open: true })}
                >
                  保証の変更履歴
                </Button>
                {canGuarantee && canCorrect && !item.cancelled_at && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => edit({ kind: 'guarantee', item, version: g.version })}
                    >
                      保証条件を訂正
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
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
        <Button type="button" onClick={() => edit({ kind: 'bonus' })}>
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
                  onClick={() => setHistory({ kind: 'bonus', id: item.id, open: true })}
                >
                  ボーナスの変更履歴
                </Button>
                {canBonus && canCorrect && !item.cancelled_at && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => edit({ kind: 'bonus', item })}
                    >
                      ボーナスを訂正
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
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
      {editor && (
        <RemunerationEditor
          key={editor.key}
          target={editor.target}
          open={editor.open}
          personId={personId}
          onClose={() => setEditor(current => current && { ...current, open: false })}
          onSaved={() => {
            setRevision(v => v + 1);
            onSaved();
          }}
        />
      )}
      {history && (
        <RemunerationHistory
          {...history}
          onClose={() => setHistory(current => current && { ...current, open: false })}
        />
      )}
    </section>
  );
}
