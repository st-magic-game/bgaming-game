package com.bgaming.diamondofjungle.entity.dto;

import com.game.base.interfaces.dto.bgaming.BgBalance;
import lombok.Data;

@Data
public class SpinResponse {
    private String api_version;
    private OutCome outcome;
    private BgBalance balance;
    private Object flow;
    private GameFeatures features = new GameFeatures();
}
