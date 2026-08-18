package com.yxoct.mail.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class StalwartProvisioningConfig {}
