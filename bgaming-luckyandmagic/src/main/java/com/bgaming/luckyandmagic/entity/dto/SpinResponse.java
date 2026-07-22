package com.bgaming.luckyandmagic.entity.dto;

import com.game.base.interfaces.dto.bgaming.BgBalance;
import com.game.base.interfaces.dto.bgaming.FlowData;
import lombok.Data;


@Data
public class SpinResponse {
    private String api_version;
    private OutCome outcome;
    private BgBalance balance;
    private Object flow;
    private Object features;
}
