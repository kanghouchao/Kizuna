import { Badge } from '@/shared/ui';

export function DisplayedBadge({ displayed }: { displayed: boolean }) {
  return displayed ? (
    <Badge variant="outline" className="border-transparent bg-success/10 text-success-strong">
      掲出中
    </Badge>
  ) : (
    <Badge variant="outline" className="border-transparent bg-muted text-muted-foreground">
      非掲出
    </Badge>
  );
}
