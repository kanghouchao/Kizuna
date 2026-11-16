import {
  BellIcon,
  CalendarCheckIcon,
  CalendarDaysIcon,
  CoinsIcon,
  HomeIcon,
  MessageCircleIcon,
  StoreIcon,
  UsersIcon,
  type LucideIcon,
} from 'lucide-react';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';

export type NavHref = (typeof STORE_SITE_NAV_PAGES)[number]['href'];

/** ナビゲーションの各ページの図・色面・添え書き。共有の一覧にページが増えると、ここに足すまで型検査が通らない。 */
export const NAV_LOOK: Record<NavHref, { Icon: LucideIcon; tone: string; sub?: string }> = {
  '/': { Icon: HomeIcon, tone: 'm-bg-card' },
  '/casts': { Icon: UsersIcon, tone: 'm-bg-peach', sub: '全員見る' },
  '/schedule': { Icon: CalendarDaysIcon, tone: 'm-bg-yellow', sub: '今日会える子' },
  '/news': { Icon: BellIcon, tone: 'm-bg-lilac', sub: '最新情報' },
  '/reviews': { Icon: MessageCircleIcon, tone: 'm-bg-sky', sub: 'みんなの声' },
  '/menu': { Icon: CoinsIcon, tone: 'm-bg-mint', sub: 'システム' },
  '/about': { Icon: StoreIcon, tone: 'm-bg-card', sub: 'アクセス' },
  '/reservation': { Icon: CalendarCheckIcon, tone: 'm-bg-pink' },
};

export const RESERVATION_HREF: NavHref = '/reservation';
