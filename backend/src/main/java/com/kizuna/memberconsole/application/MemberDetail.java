package com.kizuna.memberconsole.application;

import com.kizuna.customer.application.CustomerMemberLinkDirectory.MemberLinks;
import com.kizuna.member.application.MemberDirectoryService.MemberProfile;
import com.kizuna.point.application.PointLedgerService.ExpiryBalance;
import java.util.List;

/** 会員 1 人の照会に要る各所有モジュールの読み口の結果。関連だけが閲覧者の店舗集合で絞られている。 */
public record MemberDetail(
    MemberProfile profile, long balance, List<ExpiryBalance> balances, MemberLinks links) {}
