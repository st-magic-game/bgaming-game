package com.bgaming.carnivalbonanza.config;

import com.game.base.common.ResourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.math.BigDecimal;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties()
@PropertySource(value = {"classpath:game/global.yml", "file:./game/global.yml"},ignoreResourceNotFound = true,factory = ResourceFactory.class)

public class GlobalConfig {

    private int rotary;

    private int rotaryNum;

    private BigDecimal prizePro;

    private BigDecimal freePro;

    private List<BigDecimal> diamondSymbolPro;

    private List<BigDecimal> freeDiamondSymbolPro;

    private int baseBet;

    private int bonusBuy;

    private int freeSpinBuy;

    private BigDecimal bonusPro;

    private List<BigDecimal> multiplierPro;
}
