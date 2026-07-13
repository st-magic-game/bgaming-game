package com.bgaming.aztecclusters;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@MapperScan(value = {"com.bgaming.aztecclusters.mapper"})
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        log.info("bGaming aztec clusters :single-server:version:20260703");
        SpringApplication.run(Application.class);
    }
}
