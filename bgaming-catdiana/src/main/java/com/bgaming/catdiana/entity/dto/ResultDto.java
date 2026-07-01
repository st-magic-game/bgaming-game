package com.bgaming.catdiana.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class ResultDto {

    /**
     * 每条中奖线
     *
     * "lines":{
     *     "2":200,
     *     "4":80
     * }
     */
    private Map<Integer, BigDecimal> lines;

    /**
     * scatter奖励
     */
    private BigDecimal scatters;

    /**
     * 本局总赢分
     */
    private BigDecimal total;

    private BigDecimal coins_game;

    private BigDecimal freespins_total_wins;

    /**
     * 免费累计赢分
     * 普通模式不返回（为null）
     */
    private BigDecimal round_total_win;

}