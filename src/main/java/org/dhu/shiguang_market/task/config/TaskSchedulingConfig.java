package org.dhu.shiguang_market.task.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/** 开启 Spring 定时任务扫描。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
public class TaskSchedulingConfig {
}
