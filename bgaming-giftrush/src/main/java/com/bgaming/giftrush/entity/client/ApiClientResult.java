package com.bgaming.giftrush.entity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data@NoArgsConstructor@AllArgsConstructor@Accessors(chain = true)
public class ApiClientResult implements Serializable {

    private String api_version = "2";

    private Balance balance;

    private Flow flow;

    private Outcome outcome;

    private Map<String,Map<String, BigDecimal>> features;
}
