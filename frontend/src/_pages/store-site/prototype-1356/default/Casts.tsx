import Image from 'next/image';
import Link from 'next/link';
import { protoHref, type ProtoPageProps } from '../contract';
import Shell, { NameTile, castNo } from './Shell';

export default function Casts(props: ProtoPageProps) {
  const { casts, q } = props;

  return (
    <Shell page={props}>
      <header className="d-pagehead">
        <p className="d-label">Cast</p>
        <h1 className="d-h1">キャスト</h1>
        {casts.length > 0 && <p className="d-count">全 {casts.length} 名</p>}
      </header>

      {casts.length === 0 ? (
        <p className="d-quiet d-quiet-page">ただいまご紹介できるキャストはおりません。</p>
      ) : (
        <ul className="d-grid">
          {casts.map((cast, i) => (
            <li key={`${cast.id}-${i}`}>
              <Link href={protoHref(`/casts/${cast.id}`, q)} className="d-tile">
                {cast.photo_url ? (
                  <Image
                    src={cast.photo_url}
                    alt={cast.name ?? 'キャスト'}
                    fill
                    sizes="(min-width: 1280px) 25vw, (min-width: 768px) 33vw, 50vw"
                    className="d-cover"
                  />
                ) : (
                  <NameTile name={cast.name} />
                )}
                <span className="d-tile-no">{castNo(i)}</span>
                <span className="d-tile-name">{cast.name ?? '—'}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Shell>
  );
}
