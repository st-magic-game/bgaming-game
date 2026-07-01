package com.bgaming.catdiana.entity.dto;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class OutCome {
    private List<Integer> span_indices;

    private Map<Integer, List<Integer>> win_lines;

    private JSONObject scatters;

    private Integer freespins_left;

    private Integer freespins_performed;

    private BigDecimal freespins_wins_sum;

    private int[][] coins;

    private List<List<Integer>> coins_new;

    private int coins_respins;

    private String state;

    private String action;
}
