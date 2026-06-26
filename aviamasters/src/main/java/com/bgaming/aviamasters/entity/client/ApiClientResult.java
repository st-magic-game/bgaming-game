package com.bgaming.aviamasters.entity.client;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data@NoArgsConstructor@AllArgsConstructor@Accessors(chain = true)
public class ApiClientResult implements Serializable {

    private String api_version = "2";

    private Balance balance;

    private Flow flow;

    private Outcome outcome;

    private JSONObject features;
}
