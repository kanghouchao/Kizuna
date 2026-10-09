import { Button } from '@/shared/ui';
export function Paging({
  page,
  total,
  label,
  onPage,
}: {
  page: number;
  total: number;
  label: string;
  onPage: (page: number) => void;
}) {
  return total > 1 ? (
    <div className="flex gap-3">
      <Button
        type="button"
        variant="outline"
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
      >
        {label}の前ページ
      </Button>
      <span>
        {page + 1} / {total}
      </span>
      <Button
        type="button"
        variant="outline"
        disabled={page + 1 >= total}
        onClick={() => onPage(page + 1)}
      >
        {label}の次ページ
      </Button>
    </div>
  ) : null;
}
