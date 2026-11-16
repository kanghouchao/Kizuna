interface TallyProps {
  prefix: string;
  count: number;
}

/** 「全12名」の形の人数。縦書きの中でも 2 桁までは一字分に組む。 */
export default function Tally({ prefix, count }: TallyProps) {
  const digits = String(count);
  return (
    <>
      {prefix}
      {digits.length <= 2 ? <span className="c-tcy">{digits}</span> : digits}名
    </>
  );
}
