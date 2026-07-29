package com.technnext.hrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class HrmsApplication {

    @PostConstruct
    public void init() {
        // Store and compute all times in India Standard Time so check-in /
        // check-out timestamps match the users' local clock (server runs in UTC
        // on Render otherwise, which caused a ~5h30m offset).
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(HrmsApplication.class, args);
    }
}