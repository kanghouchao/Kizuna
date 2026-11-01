package com.kizuna.customer.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerCandidateRepository {
  record Group(ContactType type, String value, long total) {}

  record Match(ContactType type, String value, Customer customer) {}

  Page<Group> groups(Long storeId, String search, ContactType type, Pageable pageable);

  List<Match> members(Long storeId, List<Group> groups);

  Page<Customer> members(Long storeId, ContactType type, String value, Pageable pageable);
}
