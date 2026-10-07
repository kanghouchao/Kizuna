import { ReviewSearch } from '@/entities/review';
import {
  Button,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';
import { statuses, permissions } from './labels';
export function ReviewFilters({
  value,
  onChange,
  error,
}: {
  value: ReviewSearch;
  onChange: (value: ReviewSearch) => void;
  error: string | null;
}) {
  const sorts = {
    RECEIVED_DESC: '受付が新しい順',
    RECEIVED_ASC: '受付が古い順',
    CREATED_DESC: '記録が新しい順',
    CREATED_ASC: '記録が古い順',
  };
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="space-y-2">
        <Label htmlFor="review-search-name">表示名で検索</Label>
        <Input
          id="review-search-name"
          value={value.q ?? ''}
          onChange={e => onChange({ ...value, q: e.target.value })}
          aria-invalid={!!error}
          aria-describedby={error ? 'review-search-error' : undefined}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="review-search-id">口コミID</Label>
        <Input
          id="review-search-id"
          value={value.review_id ?? ''}
          onChange={e => onChange({ ...value, review_id: e.target.value })}
          aria-invalid={!!error}
          aria-describedby={error ? 'review-search-error' : undefined}
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="review-search-status">審査状態</Label>
        <Select
          value={value.status ?? '__all__'}
          onValueChange={v =>
            onChange({
              ...value,
              status: v === '__all__' ? undefined : (v as ReviewSearch['status']),
            })
          }
          items={{ __all__: 'すべて', ...statuses }}
        >
          <SelectTrigger id="review-search-status">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">すべて</SelectItem>
            {Object.entries(statuses).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="review-search-permission">公開許可</Label>
        <Select
          value={value.permission_status ?? '__all__'}
          onValueChange={v =>
            onChange({
              ...value,
              permission_status:
                v === '__all__' ? undefined : (v as ReviewSearch['permission_status']),
            })
          }
          items={{ __all__: 'すべて', ...permissions }}
        >
          <SelectTrigger id="review-search-permission">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">すべて</SelectItem>
            {Object.entries(permissions).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-2">
        <Label htmlFor="review-search-sort">並び順</Label>
        <Select
          value={value.sort ?? 'RECEIVED_DESC'}
          onValueChange={v => onChange({ ...value, sort: v as ReviewSearch['sort'] })}
          items={sorts}
        >
          <SelectTrigger id="review-search-sort">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(sorts).map(([key, label]) => (
              <SelectItem key={key} value={key}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <Button type="submit">検索</Button>
      {error && (
        <p id="review-search-error" role="alert" className="text-destructive-strong">
          {error}
        </p>
      )}
    </div>
  );
}
