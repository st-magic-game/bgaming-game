package com.bgaming.alienfruits2.config;

import com.bgaming.alienfruits2.entity.PayTable;
import com.game.base.common.ResourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties()
@PropertySource(value = {"classpath:game/paytable.yml", "file:./game/paytable.yml"},ignoreResourceNotFound = true,factory = ResourceFactory.class)
public class PayTableConfig {

    List<PayTable> payTables = new ArrayList<>();
}
