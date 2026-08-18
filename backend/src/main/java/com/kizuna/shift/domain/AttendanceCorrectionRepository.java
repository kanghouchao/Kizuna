package com.kizuna.shift.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRepository
    extends JpaRepository<AttendanceCorrection, String> {}
