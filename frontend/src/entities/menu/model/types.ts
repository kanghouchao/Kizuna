export interface MenuVO {
  name?: string;
  path?: string;
  icon?: string;
  items?: MenuVO[];
}

/** 利用者に見える一つの木。その木の権限を一つでも持つ者にだけ返る。items は根の節（ダッシュボードと区分見出し）。 */
export interface MenuTree {
  tree: 'GROUP' | 'STORE';
  items: MenuVO[];
}
