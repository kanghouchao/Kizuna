package com.kizuna.customer.api.dto;

import java.util.List;

/**
 * 重複候補の一覧。裸の配列で返さないのは {@code truncated} を載せるためで、その理由は上限を定める {@code
 * CustomerService#DUPLICATE_GROUP_LIMIT} に書いてある。
 */
public record CustomerDuplicateCandidatesResponse(
    List<CustomerDuplicateGroupResponse> groups, boolean truncated) {}
