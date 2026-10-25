package com.kizuna.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 起動時に 1 回だけ初期設定の要否を確かめ、未完了なら入口を WARN で案内する。 */
@Log4j2
@Component
@RequiredArgsConstructor
public class InitialSetupStartupNotice implements ApplicationRunner {

  private final InitialSetupService initialSetupService;

  @Override
  public void run(ApplicationArguments args) {
    initialSetupService.startupNotice().ifPresent(log::warn);
  }
}
