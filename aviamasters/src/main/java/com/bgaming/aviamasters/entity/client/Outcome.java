package com.bgaming.aviamasters.entity.client;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data@NoArgsConstructor@AllArgsConstructor@Accessors(chain = true)
public class Outcome implements Serializable {

    private BigDecimal bet = BigDecimal.ZERO;

    private List<List<Integer>> screen = null;

    private SpecialSymbols special_symbols = null;

    private JSONObject storage = new JSONObject();

    private BigDecimal win = BigDecimal.ZERO;

    private List<List<Object>> wins = new ArrayList<>();

}
