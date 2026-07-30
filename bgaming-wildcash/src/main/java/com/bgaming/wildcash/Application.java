package com.bgaming.wildcash;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        log.info("bGaming wild cash :single-server:version:20260713");
        SpringApplication.run(Application.class);
    }
}
