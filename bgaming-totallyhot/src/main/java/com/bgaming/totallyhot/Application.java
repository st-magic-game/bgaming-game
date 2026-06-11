package com.bgaming.totallyhot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        log.info("bGaming totally hot:single-server:version:20260611");
        SpringApplication.run(Application.class);
    }
}
