import { readdirSync, readFileSync, statSync } from 'fs';
import { join, relative, sep } from 'path';

/**
 * 利用者が起こす書き込みは共有の hook（useWrite / useVersionedWrite / useOperation）を通す。手書きの書き込みは
 * 「成功の案内を自分で出す」「送信中を isSubmitting で見る」のどちらかの形を取るので、その二つを見張る。
 * hook を使うファイルに手書きの書き込みが混ざる形と、案内も送信中の表示も持たない書き込みは見えない。
 */
const SRC = join(__dirname, '..');
const LAYERS = ['_pages', 'widgets', 'features'];

/** useDeleteAction は useVersionedWrite の上に、use〜Operation は useOperation の上に建つ画面ごとの包み。 */
const WRITE_HOOK = /\buse(Write|VersionedWrite|DeleteAction|\w*Operation)\b/;

/** 書き込みの hook を通さずに成功の案内を出してよいファイルと、その理由。 */
const SUCCESS_WITHOUT_HOOK: Record<string, string> = {
  'features/cast-invitation/ui/InvitationModal.tsx': 'クリップボードへの複写で、サーバへ書かない',
  '_pages/admin-staff/ui/TemporaryPasswordModal.tsx': 'クリップボードへの複写で、サーバへ書かない',
  '_pages/store-applicants/ui/ApplicantAttachmentsPanel.tsx':
    '行キーの操作（ADR 0037）。結果不明の照会・転送の中止・再送の鍵を自分で持つ',
};

/** isSubmitting を読んでよいファイルと、その理由。 */
const READS_IS_SUBMITTING: Record<string, string> = {
  '_pages/store-applicants/ui/ApplicantAttachmentsPanel.tsx':
    '行キーの操作（ADR 0037）。isSubmitting が結果不明の照会を始める時機も決める',
  '_pages/notification-deliveries/ui/NotificationDeliveriesPage.tsx':
    'useOperation の pending は応答で消える。ダイアログが閉じるまでの間を塞ぐ',
  '_pages/store-advertising-costs/ui/CostEditor.tsx':
    'useOperation の pending は応答で消える。ダイアログが閉じるまでの間を塞ぐ',
  '_pages/store-advertising-costs/ui/CopyPreview.tsx':
    'useOperation の pending は応答で消える。ダイアログが閉じるまでの間を塞ぐ',
  'widgets/remuneration-statement/ui/RemunerationEditor.tsx':
    'useOperation の pending は応答で消える。ダイアログが閉じるまでの間を塞ぐ',
};

function mainFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(entry => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) return entry === '__tests__' ? [] : mainFiles(full);
    return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [full] : [];
  });
}

const sources = new Map(
  LAYERS.flatMap(layer => mainFiles(join(SRC, layer))).map(file => [
    relative(SRC, file).split(sep).join('/'),
    readFileSync(file, 'utf8'),
  ])
);

const filesWith = (pattern: RegExp) =>
  [...sources].filter(([, source]) => pattern.test(source)).map(([file]) => file);

describe('書き込みは共有の hook を通す', () => {
  it('成功の案内を出すファイルは、書き込みの hook を使う', () => {
    const offenders = filesWith(/\bnotify\.success\(/).filter(
      file => !WRITE_HOOK.test(sources.get(file)!) && !(file in SUCCESS_WITHOUT_HOOK)
    );
    expect(offenders).toEqual([]);
  });

  it('送信中を isSubmitting で見ない（hook の busy を使う）', () => {
    const offenders = filesWith(/\bisSubmitting\b/).filter(file => !(file in READS_IS_SUBMITTING));
    expect(offenders).toEqual([]);
  });

  it('例外の一覧に載るファイルは実在し、今もその例外を要する', () => {
    for (const file of Object.keys(SUCCESS_WITHOUT_HOOK)) {
      const source = sources.get(file);
      expect({ file, calls: source?.includes('notify.success(') }).toEqual({ file, calls: true });
      expect({ file, usesHook: WRITE_HOOK.test(source!) }).toEqual({ file, usesHook: false });
    }
    for (const file of Object.keys(READS_IS_SUBMITTING)) {
      expect({ file, reads: sources.get(file)?.includes('isSubmitting') }).toEqual({
        file,
        reads: true,
      });
    }
  });
});
