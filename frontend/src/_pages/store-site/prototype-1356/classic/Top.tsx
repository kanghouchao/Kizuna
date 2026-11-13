import Image from 'next/image';
import Link from 'next/link';
import { protoHref, type ProtoPageProps } from '../contract';
import Portrait from './Portrait';
import Shell from './Shell';

const STRIP_MAX = 12;

export default function Top(props: ProtoPageProps) {
  const { store, siteConfig, casts, q } = props;
  const catchLines = (siteConfig.catch_copy ?? '')
    .split(/\r?\n/)
    .map(l => l.trim())
    .filter(l => l.length > 0);
  const banner = siteConfig.banner_url;
  const bare = !banner && catchLines.length === 0;
  const strip = casts.slice(0, STRIP_MAX);
  const count = String(casts.length);

  return (
    <Shell page={props} current="/">
      <section className={`c-open${bare ? ' c-open--bare' : ''}`} aria-label={store.name}>
        <div className="c-open-title c-v">
          <h1 className="c-open-name c-display">{store.name}</h1>
          {catchLines.map((line, i) => (
            <p key={i} className="c-open-catch c-display">
              {line}
            </p>
          ))}
        </div>

        <div className="c-open-side">
          {banner && (
            <figure className="c-frame c-open-banner" style={{ margin: 0 }}>
              <div className="c-frame-in">
                <Image
                  src={banner}
                  alt={`${store.name}の店内`}
                  fill
                  preload
                  sizes="(min-width: 1024px) 540px, (min-width: 768px) 45vw, 100vw"
                  className="c-photo"
                />
              </div>
            </figure>
          )}

          <div className="c-colophon">
            <p className="c-colophon-label">御案内</p>
            <ul className="c-colophon-links">
              <li>
                <Link href={protoHref('/schedule', q)} className="c-textlink">
                  本日の出勤
                </Link>
              </li>
              <li>
                <Link href={protoHref('/menu', q)} className="c-textlink">
                  御料金
                </Link>
              </li>
              <li>
                <Link href={protoHref('/about', q)} className="c-textlink">
                  店舗情報
                </Link>
              </li>
            </ul>
            <span className="c-age">18歳以上限定</span>
            <Link href={protoHref('/reservation', q)} className="c-seal">
              <span className="c-v">ご予約</span>
            </Link>
          </div>
        </div>
      </section>

      {siteConfig.description && (
        <section className="c-sec">
          <h2 className="c-sec-title c-v c-display">御挨拶</h2>
          <div className="c-sec-body">
            <p className="c-prose">{siteConfig.description}</p>
          </div>
        </section>
      )}

      {siteConfig.mv_url && (
        <section className="c-sec">
          <h2 className="c-sec-title c-v c-display">映像</h2>
          <div className="c-sec-body">
            <figure className="c-frame c-mv" style={{ margin: 0 }}>
              <div className="c-frame-in">
                {siteConfig.mv_type === 'video' ? (
                  <video
                    src={siteConfig.mv_url}
                    controls
                    muted
                    loop
                    playsInline
                    preload="metadata"
                    aria-label={`${store.name}の映像`}
                  />
                ) : (
                  <Image
                    src={siteConfig.mv_url}
                    alt={`${store.name}の様子`}
                    fill
                    sizes="(min-width: 1024px) 760px, 100vw"
                    className="c-photo"
                  />
                )}
              </div>
            </figure>
          </div>
        </section>
      )}

      <section className="c-sec">
        <h2 className="c-sec-title c-v c-display">在籍</h2>
        <div className="c-sec-body">
          {strip.length === 0 ? (
            <p className="c-quiet">ただいま御紹介できるキャストはおりません。</p>
          ) : (
            <ul className="c-strip">
              {strip.map((cast, i) => {
                const inner = (
                  <>
                    <Portrait
                      url={cast.photo_url}
                      name={cast.name}
                      sizes="104px"
                      glyphSize={36}
                      className="c-strip-photo"
                    />
                    <span className="c-strip-name c-v c-display">{cast.name ?? '—'}</span>
                  </>
                );
                return (
                  <li key={`${cast.id ?? 'x'}-${i}`}>
                    {cast.id ? (
                      <Link href={protoHref(`/casts/${cast.id}`, q)} className="c-strip-item">
                        {inner}
                      </Link>
                    ) : (
                      <div className="c-strip-item">{inner}</div>
                    )}
                  </li>
                );
              })}
              <li>
                <Link href={protoHref('/casts', q)} className="c-strip-more c-v">
                  <span>在籍一覧へ</span>
                  <small>
                    全{count.length <= 2 ? <span className="c-tcy">{count}</span> : count}名
                  </small>
                </Link>
              </li>
            </ul>
          )}
        </div>
      </section>

      <section className="c-sec">
        <h2 className="c-sec-title c-v c-display">御予約</h2>
        <div className="c-sec-body c-cta">
          <p>
            御来店の御予約は、こちらより承ります。初めての方も、どうぞお気軽にお申し付けくださいませ。
          </p>
          <Link href={protoHref('/reservation', q)} className="c-seal c-seal--lg">
            <span className="c-v">御予約</span>
          </Link>
          <Link href={protoHref('/auth/login', q)} className="c-textlink">
            会員の方はログイン
          </Link>
        </div>
      </section>
    </Shell>
  );
}
