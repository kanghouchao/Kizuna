import Image from 'next/image';
import Link from 'next/link';
import { castProfileRows, protoHref, type ProtoPageProps } from '../contract';
import Shell, { NameTile, castNo } from './Shell';

export default function CastDetail(props: ProtoPageProps) {
  const { cast, casts, q } = props;
  const backHref = protoHref('/casts', q);

  if (!cast) {
    return (
      <Shell page={props}>
        <p className="d-quiet d-quiet-page">キャストが見つかりません。</p>
      </Shell>
    );
  }

  const index = casts.findIndex(c => c.id === cast.id);
  const rows = castProfileRows(cast);

  return (
    <Shell page={props}>
      <article className="d-detail">
        <div className="d-detail-photo">
          {cast.photo_url ? (
            <Image
              src={cast.photo_url}
              alt={cast.name ?? 'キャスト'}
              fill
              priority
              sizes="(min-width: 1024px) 50vw, 100vw"
              className="d-cover"
            />
          ) : (
            <NameTile name={cast.name} />
          )}
        </div>

        <div className="d-detail-body">
          <Link href={backHref} className="d-textlink d-back">
            ← キャスト一覧
          </Link>
          <p className="d-label">{index >= 0 ? `Cast No. ${castNo(index)}` : 'Cast'}</p>
          <h1 className="d-detail-name">{cast.name ?? '—'}</h1>

          {rows.length > 0 && (
            <dl className="d-rows">
              {rows.map((row, i) => (
                <div key={i} className="d-row">
                  <dt>{row.label}</dt>
                  <dd>{row.value}</dd>
                </div>
              ))}
            </dl>
          )}

          {cast.introduction && (
            <section className="d-intro">
              <p className="d-label">Message</p>
              <p className="d-intro-text">{cast.introduction}</p>
            </section>
          )}

          <div className="d-detail-cta">
            <Link href="/reservation" className="d-btn">
              ご予約へ進む
            </Link>
            <Link href={backHref} className="d-textlink">
              ほかのキャストを見る
            </Link>
          </div>
        </div>
      </article>
    </Shell>
  );
}
