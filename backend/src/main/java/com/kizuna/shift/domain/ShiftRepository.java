package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShiftRepository
    extends JpaRepository<Shift, String>, JpaSpecificationExecutor<Shift> {

  List<Shift> findByWorkDateBetween(LocalDate from, LocalDate to);

  List<Shift> findByWorkDateAndStatusOrderByStartTimeAsc(LocalDate workDate, String status);
}
