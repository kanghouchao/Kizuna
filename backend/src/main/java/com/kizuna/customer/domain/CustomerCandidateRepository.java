package com.kizuna.customer.domain;

import java.util.List;

public interface CustomerCandidateRepository {
  record Group(ContactType type, String value, long total) {}

  record Match(ContactType type, String value, Customer customer) {}

  List<Group> groups(
      Long storeId,
      String search,
      ContactType type,
      ContactType afterType,
      String afterValue,
      int limit);

  List<Match> members(Long storeId, List<Group> groups);

  List<Customer> members(Long storeId, ContactType type, String value, String afterId, int limit);
}
