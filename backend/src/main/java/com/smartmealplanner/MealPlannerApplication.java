package com.smartmealplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Java owns all persisted state; Python remains an internal compute service. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MealPlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MealPlannerApplication.class, args);
    }
}
