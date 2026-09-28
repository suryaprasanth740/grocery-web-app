package com.suryaprasanth.grocery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling // runs the job that expires unpaid UPI orders
public class GroceryApplication {

    public static void main(String[] args) {
        // Store every time in UTC (same on a laptop and on Render); the browser shows local time.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(GroceryApplication.class, args);
    }
}
