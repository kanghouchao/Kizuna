package com.kizuna.repository.tenant;

import com.kizuna.model.entity.tenant.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository
    extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {}
