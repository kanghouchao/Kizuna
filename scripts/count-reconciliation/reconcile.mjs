const MAX_BYTES = 1024 * 1024;
const MAX_COUNT = 9223372036854775807n;
const CONTEXT_FIELDS = ['batch_id', 'snapshot_id', 'scope_id', 'basis_id'];
const EXEMPTION_FIELDS = [
  'category', 'exemption_code', 'reason', 'count',
  'bookkeeping_location', 'post_migration_constraint',
];
const SCHEMA_FIELDS = new Set([
  'version', 'expected_categories', 'old', 'new', 'mapping', 'counts',
  'category', 'count', 'exemptions', ...CONTEXT_FIELDS, ...EXEMPTION_FIELDS,
]);
const NOT_EVALUATED = ['amounts', 'manual_sampling', 'real_migration'];

class InvalidInput extends Error {
  constructor(code, path = '') {
    super(code);
    this.code = code;
    this.path = path;
  }
}

function invalid(code, path = '') {
  throw new InvalidInput(code, path);
}

// 値は構築せず、ストリーム上の構造・深度・配列長・復号後の重複キーだけを検査する。
// 最終的な構文と値の解釈は標準JSON.parseへ委ねる。
function structuralGuard() {
  const stack = [];
  let rootStarted = false;
  let inString = false;
  let escaped = false;
  let keyToken = null;
  let primitive = false;

  function startValue() {
    const parent = stack.at(-1);
    if (!parent) {
      if (rootStarted) invalid('INVALID_JSON');
      rootStarted = true;
      return '';
    }
    if (parent.kind === 'array') {
      if (!['valueOrEnd', 'value'].includes(parent.state)) invalid('INVALID_JSON');
      if (++parent.count > 1000) invalid('LIMIT_EXCEEDED', parent.path);
      parent.state = 'afterValue';
      return `${parent.path}/${parent.count - 1}`;
    }
    if (parent.state !== 'value') invalid('INVALID_JSON');
    parent.state = 'afterValue';
    return SCHEMA_FIELDS.has(parent.key) ? `${parent.path}/${parent.key}` : parent.path;
  }

  return {
    consume(text) {
      for (const char of text) {
        if (inString) {
          if (keyToken !== null) keyToken += char;
          if (escaped) {
            escaped = false;
          } else if (char === '\\') {
            escaped = true;
          } else if (char === '"') {
            inString = false;
            if (keyToken !== null) {
              let key;
              try { key = JSON.parse(keyToken); } catch { invalid('INVALID_JSON'); }
              const object = stack.at(-1);
              if (object.keys.has(key)) invalid('INVALID_JSON');
              object.keys.add(key);
              object.key = key;
              object.state = 'colon';
              keyToken = null;
            }
          }
          continue;
        }
        if (primitive) {
          if (!' \t\r\n,]}[{:"'.includes(char)) continue;
          primitive = false;
        }
        if (' \t\r\n'.includes(char)) continue;
        const frame = stack.at(-1);
        if (frame?.kind === 'object' && ['keyOrEnd', 'key'].includes(frame.state)) {
          if (char === '}' && frame.state === 'keyOrEnd') {
            stack.pop();
          } else if (char === '"') {
            inString = true;
            keyToken = '"';
          } else {
            invalid('INVALID_JSON');
          }
          continue;
        }
        if (frame?.state === 'colon') {
          if (char !== ':') invalid('INVALID_JSON');
          frame.state = 'value';
          continue;
        }
        if (frame?.state === 'afterValue') {
          if (char === (frame.kind === 'object' ? '}' : ']')) {
            stack.pop();
          } else if (char === ',') {
            frame.state = frame.kind === 'object' ? 'key' : 'value';
          } else {
            invalid('INVALID_JSON');
          }
          continue;
        }
        if (frame?.kind === 'array' && frame.state === 'valueOrEnd' && char === ']') {
          stack.pop();
          continue;
        }
        const path = startValue();
        if (char === '{' || char === '[') {
          if (stack.length === 64) invalid('LIMIT_EXCEEDED');
          stack.push(char === '{'
            ? { kind: 'object', state: 'keyOrEnd', keys: new Set(), path }
            : { kind: 'array', state: 'valueOrEnd', count: 0, path });
        } else if (char === '"') {
          inString = true;
        } else if ('-0123456789tfn'.includes(char)) {
          primitive = true;
        } else {
          invalid('INVALID_JSON');
        }
      }
    },
  };
}

async function readInput() {
  const chunks = [];
  const decoder = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true });
  const guard = structuralGuard();
  let bytes = 0;
  let pendingUtf8 = false;
  function decode(chunk, stream) {
    try { return decoder.decode(chunk, { stream }); } catch { invalid('INVALID_UTF8'); }
  }
  for await (const chunk of process.stdin) {
    // 複合不正入力の拒否理由がreadの分割で変わらないよう、原始バイト順で判定する。
    for (let index = 0; index < chunk.length; index++) {
      if (++bytes > MAX_BYTES) invalid('INPUT_TOO_LARGE');
      if (!pendingUtf8 && chunk[index] < 128) {
        guard.consume(String.fromCharCode(chunk[index]));
      } else {
        const text = decode(chunk.subarray(index, index + 1), true);
        pendingUtf8 = text.length === 0;
        guard.consume(text);
      }
    }
    chunks.push(chunk);
  }
  guard.consume(decode(undefined, false));
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { invalid('INVALID_JSON'); }
}

function fields(value, names, path) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    invalid('INVALID_SCHEMA', path);
  }
  const allowed = new Set(names);
  if (Object.keys(value).some(key => !allowed.has(key))) invalid('INVALID_SCHEMA', path);
  for (const name of names) {
    if (!Object.hasOwn(value, name)) invalid('INVALID_SCHEMA', `${path}/${name}`);
  }
}

function list(value, path, minimum = 0) {
  if (!Array.isArray(value) || value.length < minimum) invalid('INVALID_SCHEMA', path);
  if (value.length > 1000) invalid('LIMIT_EXCEEDED', path);
}

function identifier(value, path) {
  if (typeof value !== 'string' || value.trim() !== value || !/^[A-Za-z0-9._:-]{1,120}$/.test(value)) {
    invalid('INVALID_SCHEMA', path);
  }
}

function count(value, path) {
  if (typeof value !== 'string') invalid('INVALID_COUNT', path);
  if (value.length > 19) invalid('COUNT_OVERFLOW', path);
  if (value.trim() !== value || !/^(0|[1-9][0-9]*)$/.test(value)) invalid('INVALID_COUNT', path);
  const parsed = BigInt(value);
  if (parsed > MAX_COUNT) invalid('COUNT_OVERFLOW', path);
  return parsed;
}

function description(value, path) {
  if (typeof value !== 'string' || !value.trim() || [...value].length > 1000 || /\p{Cc}/u.test(value)) {
    invalid('INVALID_EXEMPTION', path);
  }
}

function validate(input) {
  fields(input, ['version', 'expected_categories', 'old', 'new', 'mapping', 'exemptions'], '');
  if (input.version !== 1) invalid('INVALID_SCHEMA', '/version');
  list(input.expected_categories, '/expected_categories', 1);
  const categories = new Set();
  input.expected_categories.forEach((category, index) => {
    const path = `/expected_categories/${index}`;
    identifier(category, path);
    if (categories.has(category)) invalid('DUPLICATE_CATEGORY', path);
    categories.add(category);
  });
  const sides = new Map();
  for (const side of ['old', 'new', 'mapping']) {
    const source = input[side];
    fields(source, [...CONTEXT_FIELDS, 'counts'], `/${side}`);
    for (const name of CONTEXT_FIELDS) {
      identifier(source[name], `/${side}/${name}`);
      if (source[name] !== input.old[name]) invalid('INCOMPARABLE_CONTEXT', `/${side}/${name}`);
    }
    list(source.counts, `/${side}/counts`);
    const counts = new Map();
    source.counts.forEach((row, index) => {
      const path = `/${side}/counts/${index}`;
      fields(row, ['category', 'count'], path);
      identifier(row.category, `${path}/category`);
      if (!categories.has(row.category)) invalid('UNKNOWN_CATEGORY', `${path}/category`);
      if (counts.has(row.category)) invalid('DUPLICATE_CATEGORY', `${path}/category`);
      counts.set(row.category, count(row.count, `${path}/count`));
    });
    if (counts.size !== categories.size) invalid('MISSING_CATEGORY', `/${side}/counts`);
    sides.set(side, counts);
  }
  list(input.exemptions, '/exemptions');
  input.exemptions.forEach((item, index) => {
    const path = `/exemptions/${index}`;
    fields(item, EXEMPTION_FIELDS, path);
    for (const key of ['category', 'exemption_code', 'bookkeeping_location']) {
      identifier(item[key], `${path}/${key}`);
    }
    if (!categories.has(item.category)) invalid('INVALID_EXEMPTION', `${path}/category`);
    count(item.count, `${path}/count`);
    description(item.reason, `${path}/reason`);
    description(item.post_migration_constraint, `${path}/post_migration_constraint`);
  });
  return { categories: [...categories].sort(), sides };
}

function reportBase() {
  return {
    version: 1, status: 'INVALID', context: null, categories: [], exemptions: [], errors: [],
    not_evaluated: NOT_EVALUATED,
    evidence_scope: 'SUPPLIED_ANONYMOUS_COUNTS_ONLY',
  };
}

function compare(input, validated) {
  const report = reportBase();
  report.context = Object.fromEntries(CONTEXT_FIELDS.map(key => [key, input.old[key]]));
  report.categories = validated.categories.map(category => {
    const old = validated.sides.get('old').get(category);
    const imported = validated.sides.get('new').get(category);
    const mapped = validated.sides.get('mapping').get(category);
    return {
      category, old_count: String(old), new_count: String(imported), mapping_count: String(mapped),
      new_minus_old: String(imported - old), mapping_minus_old: String(mapped - old),
      status: old === imported && old === mapped ? 'MATCHED' : 'MISMATCH',
    };
  });
  report.status = report.categories.every(row => row.status === 'MATCHED') ? 'MATCHED' : 'MISMATCH';
  const order = (left, right) => left < right ? -1 : left > right ? 1 : 0;
  report.exemptions = input.exemptions.map(item => Object.fromEntries(EXEMPTION_FIELDS.map(key => [key, item[key]])))
    .sort((left, right) => order(left.category, right.category) || order(left.exemption_code, right.exemption_code));
  return report;
}

function write(stream, text) {
  return new Promise((resolve, reject) => {
    // callback後にもerrorイベントが届くため、完了直後にlistenerを取り除かない。
    stream.once('error', reject);
    stream.write(text, error => error ? reject(error) : resolve());
  });
}

try {
  let report;
  try {
    const input = await readInput();
    const validated = validate(input);
    report = compare(input, validated);
  } catch (error) {
    if (!(error instanceof InvalidInput)) throw error;
    report = reportBase();
    report.errors = [{ code: error.code, path: error.path }];
  }
  await write(process.stdout, `${JSON.stringify(report)}\n`);
  process.exitCode = { MATCHED: 0, MISMATCH: 1, INVALID: 2 }[report.status];
} catch {
  process.exitCode = 3;
  try { await write(process.stderr, 'IO_ERROR\n'); } catch { /* 診断先の障害も原文を公開しない。 */ }
}
