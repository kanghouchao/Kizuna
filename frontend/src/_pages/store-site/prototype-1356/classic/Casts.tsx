import Link from 'next/link';
import { castProfileRows, protoHref, type ProtoPageProps } from '../contract';
import Portrait from './Portrait';
import Shell from './Shell';

export default function Casts(props: ProtoPageProps) {
  const { casts, q } = props;
  const count = String(casts.length);

  return (
    <Shell page={props} current="/casts">
      <nav className="c-crumb" aria-label="現在地">
        <Link href={protoHref('/', q)}>TOP</Link>
        <span aria-hidden="true">／</span>
        <span>在籍一覧</span>
      </nav>

      <section className="c-sec c-pagehead">
        <h1 className="c-sec-title c-v c-display">
          在籍一覧
          <small>
            全{count.length <= 2 ? <span className="c-tcy">{count}</span> : count}名
          </small>
        </h1>
        <div className="c-sec-body">
          {casts.length === 0 ? (
            <p className="c-quiet">ただいま御紹介できるキャストはおりません。</p>
          ) : (
            <ol className="c-rows">
              {casts.map((cast, i) => {
                const spec = castProfileRows(cast)
                  .slice(0, 5)
                  .map(r => `${r.label} ${r.value}`)
                  .join('　');
                const firstLine = cast.introduction?.split(/\r?\n/).find(l => l.trim()) ?? '';
                const inner = (
                  <>
                    <Portrait
                      url={cast.photo_url}
                      name={cast.name}
                      sizes="78px"
                      glyphSize={26}
                    />
                    <h2 className="c-row-name c-v c-display">{cast.name ?? '—'}</h2>
                    <div className="c-row-info">
                      <span className="c-row-no">{String(i + 1).padStart(2, '0')}</span>
                      {spec && <p className="c-row-spec">{spec}</p>}
                      {firstLine && <p className="c-row-intro">{firstLine}</p>}
                    </div>
                    <span className="c-row-go c-v" aria-hidden={cast.id ? undefined : true}>
                      {cast.id ? '詳しく' : ''}
                    </span>
                  </>
                );
                return (
                  <li key={`${cast.id ?? 'x'}-${i}`}>
                    {cast.id ? (
                      <Link href={protoHref(`/casts/${cast.id}`, q)} className="c-row">
                        {inner}
                      </Link>
                    ) : (
                      <div className="c-row">{inner}</div>
                    )}
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </section>
    </Shell>
  );
}
