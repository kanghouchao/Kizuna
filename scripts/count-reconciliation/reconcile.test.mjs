import assert from 'node:assert/strict';
import { test } from 'node:test';
import { spawn, spawnSync } from 'node:child_process';
import { closeSync, openSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const cli = fileURLToPath(new URL('./reconcile.mjs', import.meta.url));
const fixtureDirectory = new URL('../../docs/specs/0397-count-reconciliation-fixtures/', import.meta.url);
const fixture = name => JSON.parse(readFileSync(new URL(name, fixtureDirectory), 'utf8'));
const baseline = () => fixture('matched.input.json');
const sides = ['old', 'new', 'mapping'];
const contexts = ['batch_id', 'snapshot_id', 'scope_id', 'basis_id'];
const limit = '9223372036854775807';
const marker = 'SENSITIVE_MARKER_NOT_FOR_OUTPUT';

function execute(input) {
  const result = spawnSync(process.execPath, [cli], {
    input: typeof input === 'string' || Buffer.isBuffer(input) ? input : JSON.stringify(input),
    encoding: 'utf8', timeout: 10000, maxBuffer: 4 * 1024 * 1024,
  });
  if (result.error) {
    assert.equal(result.error.code, 'EPIPE');
    assert.equal(result.status, 2);
  }
  assert.equal(result.signal, null);
  assert.equal(result.stderr, '');
  assert.match(result.stdout, /^\{[^\n]*\}\n$/);
  const report = JSON.parse(result.stdout);
  assert.deepEqual(report.not_evaluated, ['amounts', 'manual_sampling', 'real_migration']);
  assert.equal(report.evidence_scope, 'SUPPLIED_ANONYMOUS_COUNTS_ONLY');
  return { ...result, report };
}

function rejected(input, code, path) {
  const result = execute(input);
  assert.equal(result.status, 2);
  assert.equal(result.report.status, 'INVALID');
  assert.equal(result.report.context, null);
  assert.deepEqual(result.report.categories, []);
  assert.deepEqual(result.report.exemptions, []);
  assert.equal(result.report.errors.length, 1);
  assert.equal(result.report.errors[0].code, code);
  if (path !== undefined) assert.equal(result.report.errors[0].path, path);
  assert.equal((result.stdout + result.stderr).includes(marker), false);
  return result;
}

function oneCategory(value = '0') {
  const input = baseline();
  input.expected_categories = ['synthetic_rows'];
  for (const side of sides) input[side].counts = [{ category: 'synthetic_rows', count: value }];
  return input;
}

function exemption(category = 'synthetic_rows') {
  return {
    category, exemption_code: 'synthetic-gap', reason: '合成試験の履歴欠落', count: '11',
    bookkeeping_location: 'synthetic-summary:exemptions', post_migration_constraint: '合成試験上の参照専用',
  };
}

function openChild(t, options = {}) {
  const child = spawn(process.execPath, [cli], { stdio: ['pipe', 'pipe', 'pipe'], ...options });
  let stdout = '';
  let stderr = '';
  child.stdout?.setEncoding('utf8').on('data', chunk => { stdout += chunk; });
  child.stderr?.setEncoding('utf8').on('data', chunk => { stderr += chunk; });
  child.stdin?.on('error', error => { assert.equal(error.code, 'EPIPE'); });
  t.after(() => { if (child.exitCode === null) child.kill(); });
  const done = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('close', (status, signal) => resolve({ status, signal, stdout, stderr }));
  });
  return { child, done };
}

test('合成の一致と差額を完全な報告・終了コードで返す', () => {
  for (const [name, status] of [['matched', 0], ['exemption-mismatch', 1]]) {
    const result = execute(fixture(`${name}.input.json`));
    assert.equal(result.status, status);
    assert.deepEqual(result.report, fixture(`${name}.expected.json`));
  }
});

test('分類配列の順序に依存せず同一入力を決定的に出力する', () => {
  const original = execute(baseline()).stdout;
  const input = baseline();
  input.expected_categories.reverse();
  for (const side of sides) input[side].counts.reverse();
  assert.equal(execute(input).stdout, original);
  assert.equal(execute(input).stdout, original);
});

test('明示的な零と2^53を超える件数を丸めない', () => {
  for (const value of ['0', '9007199254740993', limit]) {
    const result = execute(oneCategory(value));
    assert.equal(result.status, 0);
    assert.equal(result.report.categories[0].old_count, value);
    assert.equal(result.report.categories[0].new_minus_old, '0');
  }
});

test('三方の両方向差額とint64最大正負差額を正確に返す', () => {
  for (const [old, imported, mapped, newDelta, mappingDelta] of [
    ['10', '9', '11', '-1', '1'], ['10', '11', '9', '1', '-1'],
    [limit, '0', '0', `-${limit}`, `-${limit}`], ['0', limit, limit, limit, limit],
  ]) {
    const input = oneCategory(old);
    input.new.counts[0].count = imported;
    input.mapping.counts[0].count = mapped;
    const result = execute(input);
    assert.equal(result.status, 1);
    assert.equal(result.report.categories[0].new_minus_old, newDelta);
    assert.equal(result.report.categories[0].mapping_minus_old, mappingDelta);
  }
});

test('件数の型・表記・長さ・上限違反を各入力側で拒否する', () => {
  for (const side of sides) {
    for (const value of [null, 0, 1, true, {}, [], '', '-1', '-0', '+1', '1.5', '1e3', '01', ' 1', '1 ', '1\n', '1\r', '１']) {
      const input = oneCategory();
      input[side].counts[0].count = value;
      rejected(input, 'INVALID_COUNT', `/${side}/counts/0/count`);
    }
    for (const value of ['9223372036854775808', '9999999999999999999', '1'.repeat(20), '1'.repeat(50000)]) {
      const input = oneCategory();
      input[side].counts[0].count = value;
      rejected(input, 'COUNT_OVERFLOW', `/${side}/counts/0/count`);
    }
  }
});

test('必須フィールド欠落と未知フィールドを安全なpathで拒否する', () => {
  for (const key of ['version', 'expected_categories', ...sides, 'exemptions']) {
    const input = baseline();
    delete input[key];
    rejected(input, 'INVALID_SCHEMA', `/${key}`);
  }
  for (const side of sides) {
    for (const key of [...contexts, 'counts']) {
      const input = baseline();
      delete input[side][key];
      rejected(input, 'INVALID_SCHEMA', `/${side}/${key}`);
    }
    for (const key of ['category', 'count']) {
      const input = baseline();
      delete input[side].counts[0][key];
      rejected(input, 'INVALID_SCHEMA', `/${side}/counts/0/${key}`);
    }
    const input = baseline();
    input[side].counts[0][marker] = marker;
    rejected(input, 'INVALID_SCHEMA', `/${side}/counts/0`);
  }
  const input = baseline();
  input[marker] = marker;
  rejected(input, 'INVALID_SCHEMA', '');
});

test('欠落・重複・目録外の分類を三方それぞれで拒否する', () => {
  for (const side of sides) {
    const missing = baseline();
    missing[side].counts.pop();
    rejected(missing, 'MISSING_CATEGORY', `/${side}/counts`);
    const empty = oneCategory();
    empty[side].counts = [];
    rejected(empty, 'MISSING_CATEGORY', `/${side}/counts`);
    const duplicate = baseline();
    duplicate[side].counts.push(duplicate[side].counts[0]);
    rejected(duplicate, 'DUPLICATE_CATEGORY', `/${side}/counts/5/category`);
    const extra = baseline();
    extra[side].counts.push({ category: marker, count: '0' });
    rejected(extra, 'UNKNOWN_CATEGORY', `/${side}/counts/5/category`);
  }
  const input = baseline();
  input.expected_categories.push(input.expected_categories[0]);
  rejected(input, 'DUPLICATE_CATEGORY', '/expected_categories/5');
  input.expected_categories = [];
  rejected(input, 'INVALID_SCHEMA', '/expected_categories');
});

test('比較条件の四項目を各側で違えると全件比較不能となる', () => {
  for (const side of sides) {
    for (const key of contexts) {
      const input = baseline();
      input[side][key] = marker;
      rejected(input, 'INCOMPARABLE_CONTEXT');
    }
  }
});

test('識別子は正規化せず不正型・空白・長さ超過を拒否する', () => {
  for (const value of ['', ' a', 'a ', 'a\n', 'a\r', '日本語', 'x'.repeat(121), null, 1]) {
    const input = baseline();
    input.new.scope_id = value;
    rejected(input, 'INVALID_SCHEMA', '/new/scope_id');
  }
});

test('ユーザー分類にprototype名を使えてASCII順で並ぶ', () => {
  const input = baseline();
  input.expected_categories = ['__proto__', 'constructor', 'toString', 'a', 'Z'];
  for (const side of sides) input[side].counts = input.expected_categories.map(category => ({ category, count: '0' }));
  const result = execute(input);
  assert.equal(result.status, 0);
  assert.deepEqual(result.report.categories.map(row => row.category), ['Z', '__proto__', 'a', 'constructor', 'toString']);
});

test('旧件数を超える豁免と重複豁免を独立保持し差額を隠さない', () => {
  const input = oneCategory('10');
  input.new.counts[0].count = '9';
  input.exemptions = [exemption(), { ...exemption(), reason: '二件目の合成記録' }, exemption()];
  const result = execute(input);
  assert.equal(result.status, 1);
  assert.equal(result.report.categories[0].new_minus_old, '-1');
  assert.deepEqual(result.report.exemptions, input.exemptions);
  assert.equal(result.report.exemptions[0].bookkeeping_location, 'synthetic-summary:exemptions');
});

test('豁免の全必須フィールドと形式を検証し位置参照は解決しない', () => {
  for (const key of Object.keys(exemption())) {
    const input = oneCategory();
    input.exemptions = [exemption()];
    delete input.exemptions[0][key];
    rejected(input, 'INVALID_SCHEMA', `/exemptions/0/${key}`);
  }
  for (const key of ['reason', 'post_migration_constraint']) {
    for (const value of ['', ' \t ', '\u0000', '\u0085', 'x'.repeat(1001), null]) {
      const input = oneCategory();
      input.exemptions = [{ ...exemption(), [key]: value }];
      rejected(input, 'INVALID_EXEMPTION', `/exemptions/0/${key}`);
    }
  }
  const input = oneCategory();
  input.exemptions = [{ ...exemption(), bookkeeping_location: 'https://example.invalid/a' }];
  rejected(input, 'INVALID_SCHEMA', '/exemptions/0/bookkeeping_location');
  input.exemptions = [{ ...exemption(), category: marker }];
  rejected(input, 'INVALID_EXEMPTION', '/exemptions/0/category');
  input.exemptions = [{ ...exemption(), count: '-1' }];
  rejected(input, 'INVALID_COUNT', '/exemptions/0/count');
  input.exemptions[0].count = limit;
  input.exemptions[0].bookkeeping_location = 'unresolved.synthetic:reference';
  assert.equal(execute(input).status, 0);
});

test('全検証前に比較せず後方の豁免エラーでも結果を全消去する', () => {
  const input = oneCategory('10');
  input.new.counts[0].count = '9';
  input.exemptions = [{ ...exemption(), reason: marker, count: '-1' }];
  rejected(input, 'INVALID_COUNT', '/exemptions/0/count');
});

test('通常・エスケープ同名の重複キーをネスト内でも拒否する', () => {
  for (const text of [
    '{"version":1,"version":1}', '{"version":1,"\\u0076ersion":1}',
    '{"old":{"count":"1","\\u0063ount":"2"}}',
    `{"${marker}":1,"${marker}":2}`, '{"x":{"a":1,"a":2}}',
  ]) rejected(text, 'INVALID_JSON', '');
});

test('文字列内の括弧・引用符・バックスラッシュ・偽キーは構造に数えない', () => {
  const input = oneCategory();
  input.exemptions = [{ ...exemption(), reason: '{'.repeat(70) + ' "count":1, "count":2 \\ 終わり' }];
  const result = execute(input);
  assert.equal(result.status, 0);
  assert.equal(result.report.exemptions[0].reason, input.exemptions[0].reason);
});

test('JSON構文・根の型・未知フィールド・版を拒否する', () => {
  for (const text of ['', '{', '{"a":}', '{"a":1,}', '[1,]', '{}{}', '{"a":01}', '{"a":"\\x"}', 'NaN', `{"a":"${marker}\n"}`]) {
    rejected(text, 'INVALID_JSON', '');
  }
  for (const value of [null, [], true, 1]) rejected(value === null ? 'null' : JSON.stringify(value), 'INVALID_SCHEMA', '');
  const input = baseline();
  input.version = 2;
  rejected(input, 'INVALID_SCHEMA', '/version');
});

test('UTF-8の不正列・切断・BOMを置換せず拒否する', () => {
  for (const bytes of [[0xff], [0xc0, 0xaf], [0xed, 0xa0, 0x80], [0xe3, 0x81]]) {
    rejected(Buffer.from(bytes), 'INVALID_UTF8', '');
  }
  rejected(Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), Buffer.from(JSON.stringify(baseline()))]), 'INVALID_JSON', '');
});

test('入力は原始バイトで1MiBまで、1バイト超過は拒否する', () => {
  const text = JSON.stringify(baseline());
  assert.equal(execute(text + ' '.repeat(1024 * 1024 - Buffer.byteLength(text))).status, 0);
  rejected(text + ' '.repeat(1024 * 1024 - Buffer.byteLength(text) + 1), 'INPUT_TOO_LARGE', '');
});

test('JSON構造64層はschema判定へ進み65層は拒否する', () => {
  rejected('['.repeat(64) + '0' + ']'.repeat(64), 'INVALID_SCHEMA', '');
  rejected('['.repeat(65) + '0' + ']'.repeat(65), 'LIMIT_EXCEEDED', '');
});

test('分類・三方counts・豁免は各1000件まで許容する', () => {
  const input = baseline();
  input.expected_categories = Array.from({ length: 1000 }, (_, index) => `synthetic-${index}`);
  for (const side of sides) input[side].counts = input.expected_categories.map(category => ({ category, count: '0' }));
  input.exemptions = Array.from({ length: 1000 }, () => exemption('synthetic-0'));
  assert.equal(execute(input).status, 0);
  const catalog = structuredClone(input);
  catalog.expected_categories.push('synthetic-extra');
  rejected(catalog, 'LIMIT_EXCEEDED', '/expected_categories');
  for (const side of sides) {
    const extra = structuredClone(input);
    extra[side].counts.push({ category: 'synthetic-extra', count: '0' });
    rejected(extra, 'LIMIT_EXCEEDED', `/${side}/counts`);
  }
  input.exemptions.push(exemption('synthetic-0'));
  rejected(input, 'LIMIT_EXCEEDED', '/exemptions');
});

test('バイト数・深度・配列の上限超過はEOFを待たず終了する', { timeout: 10000 }, async t => {
  for (const [text, code, path] of [
    [' '.repeat(1024 * 1024 + 1), 'INPUT_TOO_LARGE', ''],
    ['['.repeat(65), 'LIMIT_EXCEEDED', ''],
    ['{"expected_categories":[' + '"synthetic",'.repeat(1000) + '"', 'LIMIT_EXCEEDED', '/expected_categories'],
    ['{"exemptions":[' + '{},'.repeat(1000) + '{', 'LIMIT_EXCEEDED', '/exemptions'],
  ]) {
    const { child, done } = openChild(t);
    child.stdin.write(text);
    const result = await done;
    assert.equal(result.status, 2);
    assert.equal(result.signal, null);
    assert.equal(result.stderr, '');
    const report = JSON.parse(result.stdout);
    assert.deepEqual(report.errors, [{ code, path }]);
    assert.equal(report.context, null);
    assert.deepEqual(report.categories, []);
    assert.deepEqual(report.exemptions, []);
  }
});

test('UTF-8とキーのエスケープがchunkを跨いでも同じ結果となる', { timeout: 10000 }, async t => {
  const input = oneCategory();
  input.exemptions = [{ ...exemption(), reason: '合成😀試験' }];
  const bytes = Buffer.from(JSON.stringify(input).replace('"version"', '"\\u0076ersion"'));
  const { child, done } = openChild(t);
  for (const byte of bytes) await new Promise((resolve, reject) => child.stdin.write(Buffer.from([byte]), error => error ? reject(error) : resolve()));
  child.stdin.end();
  const result = await done;
  assert.equal(result.status, 0);
  assert.equal(result.stderr, '');
  assert.equal(result.stdout, execute(input).stdout);
});

test('stdoutのEPIPEを固定IO_ERRORとexit 3にする', { timeout: 10000 }, async t => {
  const { child, done } = openChild(t);
  child.stdout.destroy();
  child.stdin.end(JSON.stringify(baseline()));
  const result = await done;
  assert.equal(result.status, 3);
  assert.equal(result.stderr, 'IO_ERROR\n');
  assert.equal(result.stdout, '');
});

test('stdinの読み取り障害を固定IO_ERRORとexit 3にする', () => {
  const descriptor = openSync('/dev/null', 'w');
  try {
    const result = spawnSync(process.execPath, [cli], { stdio: [descriptor, 'pipe', 'pipe'], encoding: 'utf8', timeout: 10000 });
    assert.ifError(result.error);
    assert.equal(result.status, 3);
    assert.equal(result.stdout, '');
    assert.equal(result.stderr, 'IO_ERROR\n');
  } finally {
    closeSync(descriptor);
  }
});

test('複合不正入力の拒否理由はstdinの分割に依存しない', { timeout: 10000 }, async t => {
  for (const [prefix, suffix, code] of [
    [Buffer.from('['.repeat(65)), Buffer.from([255]), 'LIMIT_EXCEEDED'],
    [Buffer.from('{"a":1,"a":'), Buffer.from([255]), 'INVALID_JSON'],
    [Buffer.from([255]), Buffer.from('['.repeat(65)), 'INVALID_UTF8'],
    [Buffer.from(' '.repeat(1024 * 1024 - 1) + '}'), Buffer.from('    '), 'INVALID_JSON'],
  ]) {
    const bulk = rejected(Buffer.concat([prefix, suffix]), code);
    const { child, done } = openChild(t);
    child.stdin.write(prefix);
    const timer = setTimeout(() => child.stdin.end(suffix), 50);
    const split = await done;
    clearTimeout(timer);
    assert.equal(split.status, 2);
    assert.equal(split.stderr, '');
    assert.equal(split.stdout, bulk.stdout);
  }
});
