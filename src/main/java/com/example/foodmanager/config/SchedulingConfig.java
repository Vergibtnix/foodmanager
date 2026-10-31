package com.example.foodmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Schaltet @Scheduled ein (für die tägliche Ablauf-Benachrichtigung in ExpiryNotificationService). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}