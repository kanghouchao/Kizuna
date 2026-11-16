import Image from 'next/image';
import Link from 'next/link';
import type { CSSProperties } from 'react';
import type { StoreSitePageProps } from '../../model/types';
import CastPortrait, { castNo } from './CastPortrait';
import Frame from './Frame';
import MemberReservationLink from './MemberReservationLink';

const TITLE_TRACKING_EM = 0.12;

/**
 * 一行で組んだときの幅（em）の見積もり。全角を 1、半角を 0.7 とし、字間を足す。
 * 実際の字幅より狭く見積もると収まらずに折り返すので、1 割の余裕を持たせる。
 */
function widthEm(text: string): number {
  const em = Array.from(text).reduce(
    (sum, ch) => sum + (ch.codePointAt(0)! > 0xff ? 1 : 0.7) + TITLE_TRACKING_EM,
    0
  );
  return Math.round(em * 110) / 100;
}

/** ヒーローの店名の大きさを決める幅。seg は空白・中黒で区切った、途中で折れない最長の部分。 */
function heroTitleStyle(name: string): CSSProperties {
  const longest = Math.max(...name.split(/[\s・]+/).map(widthEm));
  return { '--sfd-name-em': widthEm(name), '--sfd-seg-em': longest } as CSSProperties;
}

/** 店舗トップページ（default テンプレート）。 */
export default function DefaultTemplate({ store, siteConfig, casts }: StoreSitePageProps<'page'>) {
  const banner = siteConfig.banner_url;
  const mv = siteConfig.mv_url;
  const isVideo = siteConfig.mv_type === 'video';

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <section className={`sfd-hero ${banner ? 'has-img' : 'no-img'}`}>
        {banner && (
          <div className="sfd-hero-media">
            <Image src={banner} alt="" fill preload sizes="100vw" className="sfd-cover" />
          </div>
        )}
        <div className="sfd-hero-inner">
          <p className="sfd-label">Official Site</p>
          <h1 className="sfd-hero-title" style={heroTitleStyle(store.name)}>
            {store.name}
          </h1>
          <p className="sfd-hero-age">18歳以上限定</p>
        </div>
        <span className="sfd-hero-cue" aria-hidden="true">
          Scroll
        </span>
      </section>

      {siteConfig.catch_copy && (
        <section className="sfd-statement">
          <p className="sfd-statement-text">{siteConfig.catch_copy}</p>
        </section>
      )}

      <section className="sfd-castsec">
        <div className="sfd-sechead">
          <div>
            <p className="sfd-label">Cast</p>
            <h2 className="sfd-h2">キャスト</h2>
          </div>
          {casts.length > 0 && (
            <Link href="/casts" className="sfd-more">
              キャストを見る
            </Link>
          )}
        </div>
        {casts.length === 0 ? (
          <p className="sfd-quiet">ただいまご紹介できるキャストはおりません。</p>
        ) : (
          <ul className="sfd-strip">
            {casts.map((cast, i) => (
              <li key={cast.id ?? i} className="sfd-strip-item">
                <Link href={`/casts/${cast.id}`} className="sfd-strip-link">
                  <div className="sfd-portrait">
                    <CastPortrait
                      cast={cast}
                      sizes="(min-width: 1280px) 21vw, (min-width: 1024px) 26vw, (min-width: 768px) 38vw, 74vw"
                    />
                  </div>
                  <div className="sfd-strip-cap">
                    <span className="sfd-no" aria-hidden="true">
                      {castNo(i)}
                    </span>
                    <h3 className="sfd-strip-name">{cast.name ?? '—'}</h3>
                    {!!(cast.age || cast.height) && (
                      <p className="sfd-strip-meta">
                        {!!cast.age && <span>{cast.age}歳</span>}
                        {!!cast.height && <span>{cast.height}cm</span>}
                      </p>
                    )}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {mv && (
        <section className={`sfd-mv ${isVideo ? 'is-video' : ''}`} aria-label="メインビジュアル">
          {isVideo ? (
            <video src={mv} controls muted playsInline preload="metadata" className="sfd-cover">
              お使いのブラウザは動画再生に対応していません。
            </video>
          ) : (
            <Image
              src={mv}
              alt={`${store.name} のメインビジュアル`}
              fill
              sizes="100vw"
              className="sfd-cover"
            />
          )}
        </section>
      )}

      {siteConfig.description && (
        <section className="sfd-about">
          <div>
            <p className="sfd-label">Concept</p>
            <h2 className="sfd-h2">お店について</h2>
          </div>
          <p className="sfd-about-text">{siteConfig.description}</p>
        </section>
      )}

      <section className="sfd-reserve">
        <p className="sfd-label">Reservation</p>
        <h2 className="sfd-reserve-title">ご予約</h2>
        <p className="sfd-reserve-lead">ご来店のご予約は、こちらより承ります。</p>
        <Link href="/reservation" className="sfd-btn">
          ご予約へ進む
        </Link>
        <div className="sfd-reserve-sub">
          <MemberReservationLink className="sfd-textlink">会員の方は WEB予約</MemberReservationLink>
          <Link href="/auth/login" className="sfd-textlink">
            会員ログイン
          </Link>
        </div>
      </section>
    </Frame>
  );
}
