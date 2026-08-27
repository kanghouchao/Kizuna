package com.kizuna.user.api.dto;

/** アカウント面が返すロールへの参照（id と名称）。表示専用で、この面から授権は動かせない。 */
public record StaffAccountRoleRef(Long id, String name) {}
