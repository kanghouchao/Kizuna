package com.kizuna.menu.domain;

import java.util.List;

/** メニュー木の 1 ノード。CentralMenu / StoreMenu の共通読み取り面（組み立てロジックはこの interface だけを見る）。 */
public interface MenuNode {

  String getLabel();

  String getPath();

  String getIcon();

  String getPermission();

  List<? extends MenuNode> getChildren();
}
