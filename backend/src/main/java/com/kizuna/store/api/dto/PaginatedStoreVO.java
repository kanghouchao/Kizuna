package com.kizuna.store.api.dto;

import java.util.List;

/** Pagination response adapted to frontend shape used in the project. */
public record PaginatedStoreVO<T>(
    List<T> data,
    int currentPage,
    int from,
    int lastPage,
    int perPage,
    int to,
    long total,
    String firstPageUrl,
    String lastPageUrl,
    String nextPageUrl,
    String prevPageUrl) {}
