import { ChevronLeftIcon } from 'lucide-react';
import Link from 'next/link';
import { castProfileRows, protoHref, type ProtoPageProps } from '../contract';
import Portrait from './Portrait';
import Shell from './Shell';

export default function CastDetail(props: ProtoPageProps) {
  const { cast, q } = props;
  if (!cast) return null;

  const rows = castProfileRows(cast);
  const name = cast.name ?? '—';

  return (
    <Shell page={props} current="/casts">
      <nav className="c-crumb" aria-label="現在地">
        <Link href={protoHref('/', q)}>TOP</Link>
        <span aria-hidden="true">／</span>
        <Link href={protoHref('/casts', q)}>在籍一覧</Link>
        <span aria-hidden="true">／</span>
        <span>{name}</span>
      </nav>

      <article className="c-book">
        <header className="c-book-name c-v">
          <p>在籍</p>
          <h1 className="c-display">{name}</h1>
        </header>

        <div className="c-book-photo">
          <Portrait
            url={cast.photo_url}
            name={cast.name}
            sizes="(min-width: 1180px) 340px, (min-width: 768px) 300px, 70vw"
            glyphSize={96}
            preload
          />
        </div>

        {rows.length > 0 && (
          <div className="c-book-spec">
            <table className="c-spec">
              <tbody>
                {rows.map((r, i) => (
                  <tr key={`${r.label}-${i}`}>
                    <th scope="row">{r.label}</th>
                    <td>{r.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {cast.introduction && (
          <section className="c-book-intro">
            <h2>紹介</h2>
            <p className="c-prose">{cast.introduction}</p>
          </section>
        )}
      </article>

      <div className="c-book-foot">
        <Link href={protoHref('/casts', q)} className="c-textlink">
          <ChevronLeftIcon size={14} strokeWidth={1.25} aria-hidden="true" />
          在籍一覧へ戻る
        </Link>
        <div className="c-cta">
          <p className="c-quiet">{name}への御指名は、御予約より承ります。</p>
          <Link href={protoHref('/reservation', q)} className="c-seal c-seal--lg">
            <span className="c-v">御予約</span>
          </Link>
        </div>
      </div>
    </Shell>
  );
}
