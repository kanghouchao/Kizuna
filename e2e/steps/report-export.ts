import { inflateRawSync } from 'node:zlib';

export function zipEntry(bytes: Buffer, name: string): string {
  const end = bytes.lastIndexOf(Buffer.from('504b0506', 'hex'));
  if (end < 0) throw new Error('XLSX の中央ディレクトリがありません');
  let offset = bytes.readUInt32LE(end + 16);
  while (bytes.readUInt32LE(offset) === 0x02014b50) {
    const nameSize = bytes.readUInt16LE(offset + 28);
    const entry = bytes.subarray(offset + 46, offset + 46 + nameSize).toString();
    if (entry === name) {
      const size = bytes.readUInt32LE(offset + 20);
      const local = bytes.readUInt32LE(offset + 42);
      const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
      const content = bytes.subarray(start, start + size);
      return bytes.readUInt16LE(offset + 10) === 8 ? inflateRawSync(content, {maxOutputLength: 16 * 1024 * 1024}).toString() : content.toString();
    }
    offset += 46 + nameSize + bytes.readUInt16LE(offset + 30) + bytes.readUInt16LE(offset + 32);
  }
  throw new Error('XLSX のシートがありません: ' + name);
}
