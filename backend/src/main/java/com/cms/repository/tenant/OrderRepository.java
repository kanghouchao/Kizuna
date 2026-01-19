package com.cms.repository.tenant;

import com.cms.model.entity.tenant.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository
    extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {}
