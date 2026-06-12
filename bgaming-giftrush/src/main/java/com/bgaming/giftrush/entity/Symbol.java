package com.bgaming.giftrush.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Symbol {

    private int index;

    private BigDecimal multiplier;

    private int type;

    private double weight;//权重

    private String name;

}