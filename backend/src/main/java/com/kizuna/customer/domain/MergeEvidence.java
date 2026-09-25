package com.kizuna.customer.domain;

import java.util.List;

public record MergeEvidence(
    MergeSnapshot beforeSurviving,
    MergeSnapshot beforeMerged,
    MergeSnapshot afterSurviving,
    List<String> movedOrderIds,
    List<String> movedContactIds,
    List<String> movedLinkIds,
    Long actorId,
    String actorName) {}
