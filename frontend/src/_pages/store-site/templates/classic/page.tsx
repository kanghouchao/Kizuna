import Image from 'next/image';
import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import MemberReservationLink from './MemberReservationLink';
import { nameScaleStyle } from './nameScale';
import Portrait from './Portrait';
import Tally from './Tally';

/** 縦書きの題字に並べられるキャッチコピーの列数と、1 列に入る字数の目安。 */
const CATCH_MAX_COLUMNS = 4;
const CATCH_COLUMN_CHARS = 14;

/** 店舗トップページ（classic テンプレート）。縦書きの店名を右、二重罫の Banner を左に置く。 */
export default function ClassicTemplate({ store, siteConfig, casts }: StoreSitePageProps<'page'>) {
  const catchLines = (siteConfig.catch_copy ?? '')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean);
  // 縦書きは行ごとに列が増える。版面の幅に収まらない長さは、横書きで本文の側へ回す
  const catchColumns = catchLines.reduce(
    (total, line) => total + Math.ceil(Array.from(line).length / CATCH_COLUMN_CHARS),
    0
  );
  const catchVertical = catchColumns <= CATCH_MAX_COLUMNS;
  const banner = siteConfig.banner_url;
  const bare = !banner && catchLines.length === 0;

  return (
    <Frame store={store} siteConfig={siteConfig} current="/">
      <section className={`c-open${bare ? ' c-open--bare' : ''}`}>
        <div className="c-open-title c-v">
          <h1 className="c-open-name c-name c-display" style={nameScaleStyle(store.name)}>
            {store.name}
          </h1>
          {catchVertical &&
            catchLines.map((line, index) => (
              <p key={index} className="c-open-catch c-display">
                {line}
              </p>
            ))}
        </div>

        <div className="c-open-side">
          {!catchVertical && <p className="c-open-catch-flat c-display">{catchLines.join('\n')}</p>}

          {banner && (
            <div className="c-frame c-open-banner">
              <div className="c-frame-in">
                <Image
                  src={banner}
                  alt={store.name}
                  fill
                  preload
                  sizes="(min-width: 1024px) 540px, (min-width: 768px) 45vw, 100vw"
                  className="c-photo"
                />
              </div>
            </div>
          )}

          <div className="c-colophon">
            <p className="c-colophon-label">ご案内</p>
            <ul className="c-colophon-links">
              <li>
                <Link href="/schedule" className="c-textlink">
                  本日の出勤
                </Link>
              </li>
              <li>
                <Link href="/menu" className="c-textlink">
                  料金
                </Link>
              </li>
              <li>
                <Link href="/about" className="c-textlink">
                  店舗情報
                </Link>
              </li>
            </ul>
            <span className="c-age">18歳以上限定</span>
            <Link href="/reservation" className="c-seal">
              <span className="c-v">ご予約</span>
            </Link>
          </div>
        </div>
      </section>

      {siteConfig.description && (
        <section className="c-sec">
          <h2 className="c-sec-title c-v c-display">ご挨拶</h2>
          <div className="c-sec-body">
            <p className="c-prose">{siteConfig.description}</p>
          </div>
        </section>
      )}

      {siteConfig.mv_url && (
        <section className="c-sec">
          <h2 className="c-sec-title c-v c-display">映像</h2>
          <div className="c-sec-body">
            <div className="c-frame c-mv">
              <div className="c-frame-in">
                {siteConfig.mv_type === 'video' ? (
                  <video
                    src={siteConfig.mv_url}
                    controls
                    playsInline
                    preload="metadata"
                    aria-label="メインビジュアル動画"
                  />
                ) : (
                  <Image
                    src={siteConfig.mv_url}
                    alt="メインビジュアル"
                    fill
                    sizes="(min-width: 1024px) 760px, 100vw"
                    className="c-photo"
                  />
                )}
              </div>
            </div>
          </div>
        </section>
      )}

      <section className="c-sec">
        <h2 className="c-sec-title c-v c-display">キャスト</h2>
        <div className="c-sec-body">
          {casts.length === 0 ? (
            <p className="c-quiet">キャスト情報は準備中です</p>
          ) : (
            <ul className="c-strip">
              {casts.map(cast => (
                <li key={cast.id}>
                  <Link href={`/casts/${cast.id}`} className="c-strip-item">
                    <Portrait
                      url={cast.photo_url}
                      name={cast.name}
                      sizes="104px"
                      className="c-strip-photo"
                    />
                    <h3 className="c-strip-name c-v c-name c-display">{cast.name}</h3>
                  </Link>
                </li>
              ))}
              <li>
                <Link href="/casts" className="c-strip-more c-v">
                  <span>キャスト一覧へ</span>
                  <small>
                    <Tally prefix="全" count={casts.length} />
                  </small>
                </Link>
              </li>
            </ul>
          )}
        </div>
      </section>

      <section className="c-sec">
        <h2 className="c-sec-title c-v c-display">ご予約</h2>
        <div className="c-sec-body c-cta">
          <p>
            ご来店のご予約は、こちらより承ります。初めての方も、どうぞお気軽にお申し付けください。
          </p>
          <Link href="/reservation" className="c-seal c-seal--lg">
            <span className="c-v">ご予約</span>
          </Link>
          <div className="c-cta-links">
            <MemberReservationLink className="c-textlink">会員予約</MemberReservationLink>
            <Link href="/auth/login" className="c-textlink">
              ログイン
            </Link>
          </div>
        </div>
      </section>
    </Frame>
  );
}
