import type { ProtoPageProps } from '../contract';
import { CastGrid, EmptyCasts, Shell } from './parts';

export default function Casts(props: ProtoPageProps) {
  const { casts, q } = props;
  return (
    <Shell props={props}>
      <header className="m-pop flex flex-wrap items-end justify-between gap-3 pt-2 pb-6">
        <div>
          <p className="text-xs font-bold tracking-[0.2em] text-[#c2185b]">CAST</p>
          <h1 className="m-display mt-1 text-4xl sm:text-5xl">キャスト</h1>
        </div>
        {casts.length > 0 && (
          <p className="m-bg-yellow m-border rounded-full px-4 py-1 text-sm font-bold">
            在籍 {casts.length}人
          </p>
        )}
      </header>
      {casts.length > 0 ? <CastGrid casts={casts} q={q} /> : <EmptyCasts />}
    </Shell>
  );
}
