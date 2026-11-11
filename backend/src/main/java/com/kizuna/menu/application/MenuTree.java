package com.kizuna.menu.application;

import com.kizuna.user.domain.PermissionTree;
import java.util.List;

/** 利用者に見える一つの木（グループ / 店舗）。items はその木の根の節（ダッシュボードと区分見出し）。 */
public record MenuTree(PermissionTree tree, List<MenuNode> items) {}
