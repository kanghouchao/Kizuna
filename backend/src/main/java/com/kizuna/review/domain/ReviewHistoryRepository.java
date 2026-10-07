package com.kizuna.review.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReviewHistoryRepository
    extends JpaRepository<ReviewHistory, String>, JpaSpecificationExecutor<ReviewHistory> {}
