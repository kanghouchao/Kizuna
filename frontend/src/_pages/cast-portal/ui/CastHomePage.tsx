'use client';

import Link from 'next/link';
import { ChevronRightIcon } from 'lucide-react';
import { Card } from '@/shared/ui';

const LINKS = [
  { href: '/cast/schedule', label: '出勤予定を見る' },
  { href: '/cast/remunerations', label: '報酬を見る' },
] as const;

/** ホームタブの仮の画面。今日の出勤・やること・累計報酬はホームの作り直しで載せる。 */
export function CastHomePage() {
  return (
    <Card className="py-0">
      <nav aria-label="よく使う画面">
        {LINKS.map(link => (
          <Link
            key={link.href}
            href={link.href}
            className="flex min-h-11 items-center justify-between rounded-xl px-4 hover:bg-muted focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
          >
            {link.label}
            <ChevronRightIcon className="size-5 text-muted-foreground" aria-hidden="true" />
          </Link>
        ))}
      </nav>
    </Card>
  );
}
