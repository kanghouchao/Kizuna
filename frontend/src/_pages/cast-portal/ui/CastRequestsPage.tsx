/** 希望提出タブの準備中プレースホルダ（本体は別票）。 */
export function CastRequestsPage() {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-16 text-center">
      <p className="text-sm font-medium text-gray-900">希望提出は準備中です</p>
      <p className="text-xs text-gray-500">開通までしばらくお待ちください。</p>
    </div>
  );
}
