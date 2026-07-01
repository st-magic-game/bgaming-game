package com.bgaming.catdiana.entity.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SpinResponse {
    private JSONObject bets;

    private OutCome game;

    private ResultDto result;

    private BigDecimal balance;

    private List<String> available_commands;
}
