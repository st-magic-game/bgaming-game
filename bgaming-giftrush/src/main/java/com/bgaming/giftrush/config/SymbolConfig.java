package com.bgaming.giftrush.config;


import com.bgaming.giftrush.entity.Symbol;
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
@PropertySource(value = {"classpath:game/symbol.yml", "file:./game/symbol.yml"},ignoreResourceNotFound = true,factory = ResourceFactory.class)
public class SymbolConfig {

    private List<Symbol> symbols = new ArrayList<>();
}
