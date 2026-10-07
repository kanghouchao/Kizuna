package com.kizuna.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.task.execution.TaskHandler;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TaskOptionsTest {
  @Test
  void catalogIsStableAndBounded() {
    var first = handler("A");
    var last = handler("Z");
    assertThat(new TaskRegistry(List.of(last, first)).registered()).containsExactly(first, last);
    assertThat(
            new TaskRegistry(IntStream.range(0, 32).mapToObj(i -> handler("T" + i)).toList())
                .registered())
        .hasSize(32);
    assertThatThrownBy(
            () -> new TaskRegistry(IntStream.range(0, 33).mapToObj(i -> handler("T" + i)).toList()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new TaskRegistry(List.of(first, first)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static TaskHandler handler(String name) {
    var handler = mock(TaskHandler.class);
    when(handler.name()).thenReturn(name);
    return handler;
  }
}
