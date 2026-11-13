// PROTOTYPE（#1356）: 「3 テンプレートの版面骨格を、トップ・キャスト一覧・キャスト詳細の既存ルート上で
// ?variant=default|modern|classic（＋ ?data=real|bare|many）で見比べる」。
// データ取得はこの殻が行い、variant は取得済みのデータを描くだけ（本実装で採る形の下見を兼ねる）。
import { notFound } from 'next/navigation';
import { Suspense, type ComponentType, type ReactNode } from 'react';
import type { PublicStore } from '@/shared/api/index.server';
import { storefrontService } from '../api/storefront';
import type { Cast, SiteConfig } from '../model/types';
import ClassicCastDetail from './classic/CastDetail';
import ClassicCasts from './classic/Casts';
import ClassicTop from './classic/Top';
import {
  PROTO_DATA_MODES,
  PROTO_PAGES,
  PROTO_VARIANTS,
  type ProtoDataMode,
  type ProtoPage,
  type ProtoPageProps,
  type ProtoVariant,
} from './contract';
import DefaultCastDetail from './default/CastDetail';
import DefaultCasts from './default/Casts';
import DefaultTop from './default/Top';
import ModernCastDetail from './modern/CastDetail';
import ModernCasts from './modern/Casts';
import ModernTop from './modern/Top';
import PrototypeSwitcher from './PrototypeSwitcher';

const REGISTRY: Record<ProtoVariant, Record<ProtoPage, ComponentType<ProtoPageProps>>> = {
  default: { page: DefaultTop, casts: DefaultCasts, 'cast-detail': DefaultCastDetail },
  modern: { page: ModernTop, casts: ModernCasts, 'cast-detail': ModernCastDetail },
  classic: { page: ClassicTop, casts: ClassicCasts, 'cast-detail': ClassicCastDetail },
};

function applyDataMode(
  mode: ProtoDataMode,
  siteConfig: SiteConfig,
  casts: Cast[]
): { siteConfig: SiteConfig; casts: Cast[] } {
  if (mode === 'bare') {
    return {
      siteConfig: { template_key: siteConfig.template_key, sns_links: [], partner_links: [] },
      casts: [],
    };
  }
  if (mode === 'many' && casts.length > 0) {
    return { siteConfig, casts: Array.from({ length: 40 }, (_, i) => casts[i % casts.length]) };
  }
  return { siteConfig, casts };
}

interface PrototypeFrameProps {
  page: string;
  pageProps: Record<string, unknown>;
  store: PublicStore;
  searchParams?: Record<string, string | string[] | undefined>;
  /** variant 指定が無いときに描く現行のページ */
  children: ReactNode;
}

export default async function PrototypeFrame({
  page,
  pageProps,
  store,
  searchParams,
  children,
}: PrototypeFrameProps) {
  if (!(PROTO_PAGES as readonly string[]).includes(page)) return children;

  const variant = searchParams?.variant;
  const switcher = (
    <Suspense fallback={null}>
      <PrototypeSwitcher />
    </Suspense>
  );
  if (typeof variant !== 'string' || !(PROTO_VARIANTS as readonly string[]).includes(variant)) {
    return (
      <>
        {children}
        {switcher}
      </>
    );
  }

  const rawMode = searchParams?.data;
  const mode: ProtoDataMode =
    typeof rawMode === 'string' && (PROTO_DATA_MODES as readonly string[]).includes(rawMode)
      ? (rawMode as ProtoDataMode)
      : 'real';

  const fetched = await storefrontService.getPageData(store.id);
  let cast: Cast | undefined;
  if (page === 'cast-detail') {
    cast = fetched.casts.find(c => c.id === pageProps.castId);
    if (!cast) notFound();
  }
  const { siteConfig, casts } = applyDataMode(mode, fetched.siteConfig, fetched.casts);
  const q = `?variant=${variant}${mode === 'real' ? '' : `&data=${mode}`}`;

  const Page = REGISTRY[variant as ProtoVariant][page as ProtoPage];
  return (
    <>
      <div data-template={variant}>
        <Page store={store} siteConfig={siteConfig} casts={casts} cast={cast} q={q} />
      </div>
      {switcher}
    </>
  );
}
