package com.bgaming.diamondofjungle.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class GameFeatures {
    private String volatility;
    private Integer freespins_issued;
    private Integer freespins_left;
    private BigDecimal total_fs_win = BigDecimal.ZERO;
}
