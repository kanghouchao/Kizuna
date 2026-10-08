export function validateRemunerationPeriod(mode: 'day' | 'month', value: string): true | string {
  if (mode === 'month') {
    return (
      /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(value) || '対象月は YYYY-MM 形式で入力してください'
    );
  }
  return (
    (/^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/.test(value) &&
      !Number.isNaN(Date.parse(value)) &&
      new Date(value).toISOString().slice(0, 10) === value) ||
    '営業日は YYYY-MM-DD 形式の有効な日付で入力してください'
  );
}
