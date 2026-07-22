package com.bgaming.luckyandmagic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        log.info("bGaming lucky and magic :single-server:version:20260714");
        SpringApplication.run(Application.class);
    }
}
