package com.bgaming.alienfruits.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayTable {

    private int type;

    private Map<String, BigDecimal> multiplierMap;
}
