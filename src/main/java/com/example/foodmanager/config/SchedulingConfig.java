package com.example.foodmanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Schaltet @Scheduled ein (wird für die tägliche Benachrichtigung gebraucht). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}