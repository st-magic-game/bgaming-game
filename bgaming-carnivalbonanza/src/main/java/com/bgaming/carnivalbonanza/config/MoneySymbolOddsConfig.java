package com.bgaming.carnivalbonanza.config;

import com.alibaba.fastjson.annotation.JSONField;
import com.game.base.common.ResourceFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.Serializable;

@Data
@Configuration
@ConfigurationProperties()
@PropertySource(value = {"classpath:game/moneySymbolOdds.yml", "file:./game/moneySymbolOdds.yml"},ignoreResourceNotFound = true,factory = ResourceFactory.class)
public class MoneySymbolOddsConfig implements Serializable {

    @JSONField(name = "SC1")
    private int SC1;
    @JSONField(name = "SC2")
    private int SC2;
    @JSONField(name = "SC3")
    private int SC3;
    @JSONField(name = "SC5")
    private int SC5;
    @JSONField(name = "SC10")
    private int SC10;
    @JSONField(name = "SC15")
    private int SC15;
    @JSONField(name = "SC20")
    private int SC20;
    @JSONField(name = "Mini")
    private int Mini;
    @JSONField(name = "SC50")
    private int SC50;
    @JSONField(name = "Major")
    private int Major;
    @JSONField(name = "Grand")
    private int Grand;
    @JSONField(name = "ReSpin")
    private int ReSpin;


}
