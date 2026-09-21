package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactType;
import java.util.List;

/** 同じ種類・値の有効連絡先を共有する顧客。20 件を超える組の customers は空で、専用一覧で続きを読む。 */
public record CustomerDuplicateGroupResponse(
    ContactType matchedType,
    String matchedValue,
    long total,
    List<CustomerMergeComparisonResponse> customers) {}
