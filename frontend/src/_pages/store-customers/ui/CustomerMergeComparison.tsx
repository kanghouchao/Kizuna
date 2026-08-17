'use client';

import { CustomerDuplicateResponse } from '@/entities/customer';
import { Button, Label, RadioGroup, RadioGroupItem } from '@/shared/ui';

/** 見比べる項目。値の取り出しは行ごとに同じ関数を通し、左右で違う整形にならないようにする。 */
const FIELDS: { label: string; value: (row: CustomerDuplicateResponse) => string }[] = [
  { label: '名前', value: row => row.name || '-' },
  { label: '電話番号', value: row => row.phone_number || '-' },
  { label: '電話番号2', value: row => row.phone_number2 || '-' },
  { label: '住所', value: row => row.address || '-' },
  { label: '建物名', value: row => row.building_name || '-' },
  { label: '区分', value: row => row.classification || '-' },
  { label: 'ランク', value: row => row.rank || '-' },
  { label: 'LINE ID', value: row => row.line_id || '-' },
  { label: '利用エリア', value: row => row.usage_areas || '-' },
  // 未設定は「なし」ではない。応答は non_null 直列化なので欄ごと欠けて届き、真偽値へ潰すと
  // 持っていない事実を断言することになる（別人を見分けるための画面で、それが一番やってはいけない）
  {
    label: 'ペット',
    value: row => (row.has_pet === undefined ? '-' : row.has_pet ? 'あり' : 'なし'),
  },
  { label: 'NG 区分', value: row => row.ng_type || '-' },
  { label: 'NG 内容', value: row => row.ng_content || '-' },
  { label: '受注件数', value: row => `${row.order_count} 件` },
  { label: '会員紐づけ', value: row => (row.member_linked ? '紐づけ済み' : '未紐づけ') },
];

interface CustomerMergeComparisonProps {
  /** 見比べる 2 行。並びは候補一覧の並びのまま。 */
  rows: [CustomerDuplicateResponse, CustomerDuplicateResponse];
  /** 台帳に残す行。人が選ぶまで null で、機械は決めない。 */
  survivingId: string | null;
  onSurvivingChange: (customerId: string) => void;
  onMerge: () => void;
  disabled: boolean;
}

/**
 * 選んだ 2 行を並べて見比べる区画。
 *
 * 値を選んで合わせる UI は持たない。フィールド値の自動合併も空欄の自動補完も採らないのが ADR 0010 の
 * 裁定で、存続行へ移したい値は人が見て転記する（どの値が本人申告でどれが機械転記かを消さないため）。
 * ここが答えるのは「この 2 行は同一人物か」だけである。
 */
export function CustomerMergeComparison({
  rows,
  survivingId,
  onSurvivingChange,
  onMerge,
  disabled,
}: CustomerMergeComparisonProps) {
  return (
    <div className="space-y-4 border-t bg-muted/30 px-6 py-5">
      <div>
        <h3 className="text-sm font-medium text-foreground">2 行を見比べる</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          同一人物かどうかを確かめてから、台帳に残す行を選んでください。
        </p>
      </div>

      <RadioGroup
        value={survivingId ?? ''}
        onValueChange={value => onSurvivingChange(String(value))}
        className="grid-cols-1 gap-0 overflow-x-auto"
      >
        {/* 候補一覧の表と同じ画面に並ぶので、表そのものに名前を付けて読み分けられるようにする */}
        <table className="w-full text-sm" aria-label="2 行の比較">
          <thead>
            <tr>
              <th className="w-32 py-2 text-left font-medium text-muted-foreground">項目</th>
              {rows.map(row => (
                <th key={row.id} className="py-2 text-left font-medium text-foreground">
                  <Label className="flex items-center gap-2 font-medium">
                    {/* 名前を含む aria-label を持たせる。両列とも見出しは同じ文言なので、
                        それだけでは読み上げでどちらを残すのか判らない */}
                    <RadioGroupItem value={row.id ?? ''} aria-label={`${row.name} を残す`} />
                    この行を残す
                  </Label>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {FIELDS.map(field => {
              // 値が食い違う項目は目立たせる。畳んでよいかの判断はここが分かれ目になる
              const differs = field.value(rows[0]) !== field.value(rows[1]);
              return (
                <tr key={field.label} className={differs ? 'bg-warning/10' : undefined}>
                  <td className="py-1.5 align-top text-muted-foreground">{field.label}</td>
                  {rows.map(row => (
                    <td key={row.id} className="py-1.5 align-top text-foreground">
                      {field.value(row)}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </RadioGroup>

      <div className="flex flex-wrap items-center justify-end gap-3">
        {/* 残す行を既定で埋めない。台帳に残る行を機械に決めさせないのが ADR 0010 の裁定 */}
        {survivingId === null && (
          <p className="text-sm text-muted-foreground">台帳に残す行を選んでください。</p>
        )}
        <Button type="button" onClick={onMerge} disabled={disabled || survivingId === null}>
          統合する
        </Button>
      </div>
    </div>
  );
}
