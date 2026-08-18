package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.Attendance;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttendanceMapper {

  AttendanceResponse toResponse(Attendance attendance);
}
