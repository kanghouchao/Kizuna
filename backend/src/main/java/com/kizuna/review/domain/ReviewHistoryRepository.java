package com.kizuna.review.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReviewHistoryRepository
    extends StoreScopedRepository<ReviewHistory>, JpaSpecificationExecutor<ReviewHistory> {}
