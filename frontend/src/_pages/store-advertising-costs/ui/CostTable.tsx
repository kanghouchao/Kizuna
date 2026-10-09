import { categoryLabels, type Cost } from '../api/advertising';
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui';
export function CostTable({
  rows,
  onEdit,
  onDelete,
  disabled = false,
}: {
  rows: Cost[];
  onEdit?: (c: Cost) => void;
  onDelete?: (c: Cost) => void;
  disabled?: boolean;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {[
            '区分',
            '媒体',
            '広告会社',
            'プラン',
            '問い合わせ人数',
            '金額（円）',
            ...(onEdit ? ['操作'] : []),
          ].map(h => (
            <TableHead key={h}>{h}</TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map(c => (
          <TableRow key={c.id}>
            <TableCell>{categoryLabels[c.category]}</TableCell>
            <TableCell className="max-w-48 break-words">{c.media_name}</TableCell>
            <TableCell className="max-w-48 break-words">{c.agency_name ?? '未設定'}</TableCell>
            <TableCell className="max-w-48 break-words">{c.plan_name ?? '未設定'}</TableCell>
            <TableCell>
              {c.inquiry_count === null ? '未計測' : c.inquiry_count.toLocaleString() + '人'}
            </TableCell>
            <TableCell>{c.amount.toLocaleString()}</TableCell>
            {onEdit && (
              <TableCell>
                <div className="flex gap-2">
                  <Button variant="outline" disabled={disabled} onClick={() => onEdit(c)}>
                    編集
                  </Button>
                  <Button variant="outline" disabled={disabled} onClick={() => onDelete?.(c)}>
                    削除
                  </Button>
                </div>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
