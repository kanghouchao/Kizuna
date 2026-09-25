package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import com.kizuna.customer.domain.MergeSnapshot;
import java.util.List;

public record CustomerMergePreviewResponse(
    MergeSnapshot surviving,
    MergeSnapshot merged,
    MergeProfile profile,
    MergePreferences preferredContacts,
    List<ContactType> preferenceConflicts,
    boolean memberLinked,
    String finalMemberCode,
    Long pointBalance,
    long unfinishedOrderCount,
    int movedOrderCount,
    int movedContactCount,
    int movedLinkCount,
    String previewToken) {}
