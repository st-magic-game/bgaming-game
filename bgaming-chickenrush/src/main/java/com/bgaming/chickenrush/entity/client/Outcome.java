package com.bgaming.chickenrush.entity.client;

import com.alibaba.fastjson.annotation.JSONField;
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

    private List<List<String>> screen = new ArrayList<>();

    private SpecialSymbols special_symbols = new SpecialSymbols();

    private Storage storage = new Storage();

    private BigDecimal win = BigDecimal.ZERO;

    private List<List<Object>> wins = new ArrayList<>();

    private int freespins_issued;

}
