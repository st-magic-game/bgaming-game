package com.bgaming.diamondofjungle;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@MapperScan(value = {"com.bgaming.diamondofjungle.mapper"})
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        log.info("bGaming diamond of jungle:single-server:version:20260715");
        SpringApplication.run(Application.class);
    }
}
