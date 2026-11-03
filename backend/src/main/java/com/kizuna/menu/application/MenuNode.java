package com.kizuna.menu.application;

import java.util.List;

/** 利用者に見えるメニュー木の節。items は可視な子だけを持ち、子が無ければ空のリスト。 */
public record MenuNode(String name, String path, String icon, List<MenuNode> items) {}
