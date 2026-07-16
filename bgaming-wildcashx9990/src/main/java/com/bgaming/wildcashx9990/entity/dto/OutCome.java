package com.bgaming.wildcashx9990.entity.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutCome {
    private BigDecimal bet = BigDecimal.ZERO;
    private List<List<String>> screen = new ArrayList<>();
    private JSONObject special_symbols = new JSONObject();
    private Object storage = null;
    private BigDecimal win = BigDecimal.ZERO;
    // [["line",200,[1,0,0],5],["line",200,[null,null,2,2,1],6]]
    private List<List<Object>> wins = new ArrayList<>();
}
