package com.expertiz.sentinel.core.identity.internal.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées d'{@code identity} : purge des sessions et des compteurs ({@link SessionCleanupJob}). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
