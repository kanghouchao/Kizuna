/**
 * 出勤表の空状態プレースホルダ（Server Component）。
 *
 * ponytail: 出勤スケジュールの実データ源は #168（排班機能）実装後に接続する。
 * 現時点は API が無いため固定の空状態のみ表示する。
 */
export default function SchedulePlaceholder() {
  return (
    <section className="py-16 md:py-24 px-5 sm:px-6" style={{ background: '#080808' }}>
      <div className="max-w-2xl mx-auto text-center">
        <p
          className="text-[#F8F4F0]/70 text-base md:text-lg font-light tracking-[0.15em] mb-4"
          style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
        >
          本日の出勤情報はありません
        </p>
        <p
          className="text-[#F8F4F0]/30 text-xs md:text-sm leading-relaxed tracking-wider"
          style={{ fontFamily: "'Noto Serif JP', serif" }}
        >
          出勤情報は準備中です。最新の情報はお電話でお問い合わせください。
        </p>
      </div>
    </section>
  );
}
