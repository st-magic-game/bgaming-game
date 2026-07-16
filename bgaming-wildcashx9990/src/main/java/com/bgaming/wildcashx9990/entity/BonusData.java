package com.bgaming.wildcashx9990.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class BonusData implements Serializable {
    private int bonus_multiplier;
    private int scatters_count;
    private int scatters_multiplier;
    private int total_multiplier;
}
