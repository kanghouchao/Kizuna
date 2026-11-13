import Image from 'next/image';
import Link from 'next/link';
import { protoHref, type ProtoPageProps } from '../contract';
import Shell, { NameTile, castNo } from './Shell';

export default function Top(props: ProtoPageProps) {
  const { store, siteConfig, casts, q } = props;
  const banner = siteConfig.banner_url;
  const mv = siteConfig.mv_url;
  const isVideo = siteConfig.mv_type === 'video';

  return (
    <Shell page={props}>
      <section className={`d-hero ${banner ? 'has-img' : 'no-img'}`}>
        {banner && (
          <div className="d-hero-media">
            <Image
              src={banner}
              alt={`${store.name} のバナー`}
              fill
              priority
              sizes="100vw"
              className="d-cover"
            />
          </div>
        )}
        <div className="d-hero-inner">
          <p className="d-label">Official Site</p>
          <h1 className="d-hero-title">{store.name}</h1>
          <p className="d-hero-age">18歳以上限定</p>
        </div>
        <span className="d-hero-cue" aria-hidden="true">
          Scroll
        </span>
      </section>

      {siteConfig.catch_copy && (
        <section className="d-statement">
          <p className="d-statement-text">{siteConfig.catch_copy}</p>
        </section>
      )}

      <section className="d-castsec">
        <div className="d-sechead">
          <div>
            <p className="d-label">Cast</p>
            <h2 className="d-h2">キャスト</h2>
          </div>
          {casts.length > 0 && (
            <Link href={protoHref('/casts', q)} className="d-more">
              すべて見る
            </Link>
          )}
        </div>
        {casts.length === 0 ? (
          <p className="d-quiet">ただいまご紹介できるキャストはおりません。</p>
        ) : (
          <ul className="d-strip">
            {casts.map((cast, i) => (
              <li key={`${cast.id}-${i}`} className="d-strip-item">
                <Link href={protoHref(`/casts/${cast.id}`, q)} className="d-strip-link">
                  <div className="d-portrait">
                    {cast.photo_url ? (
                      <Image
                        src={cast.photo_url}
                        alt={cast.name ?? 'キャスト'}
                        fill
                        sizes="(min-width: 1280px) 21vw, (min-width: 1024px) 26vw, (min-width: 768px) 38vw, 74vw"
                        className="d-cover"
                      />
                    ) : (
                      <NameTile name={cast.name} />
                    )}
                  </div>
                  <p className="d-strip-cap">
                    <span className="d-no">{castNo(i)}</span>
                    <span className="d-strip-name">{cast.name ?? '—'}</span>
                  </p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {mv && (
        <section className={`d-mv ${isVideo ? 'is-video' : ''}`} aria-label="メインビジュアル">
          {isVideo ? (
            <video src={mv} controls muted playsInline preload="metadata" className="d-cover">
              お使いのブラウザは動画再生に対応していません。
            </video>
          ) : (
            <Image
              src={mv}
              alt={`${store.name} のメインビジュアル`}
              fill
              sizes="100vw"
              className="d-cover"
            />
          )}
        </section>
      )}

      {siteConfig.description && (
        <section className="d-about">
          <div className="d-about-head">
            <p className="d-label">Concept</p>
            <h2 className="d-h2">お店について</h2>
          </div>
          <p className="d-about-text">{siteConfig.description}</p>
        </section>
      )}

      <section className="d-reserve">
        <p className="d-label">Reservation</p>
        <h2 className="d-reserve-title">ご予約</h2>
        <p className="d-reserve-lead">ご来店のご予約は、こちらより承ります。</p>
        <Link href="/reservation" className="d-btn">
          ご予約へ進む
        </Link>
        <Link href="/auth/login" className="d-textlink">
          会員の方はログイン
        </Link>
      </section>
    </Shell>
  );
}
