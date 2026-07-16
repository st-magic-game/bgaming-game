package com.bgaming.wildcashx9990.service;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.wildcashx9990.config.LotteryConfig;
import com.game.base.application.service.IPlayerService;
import com.game.base.domain.player.Player;
import com.game.base.interfaces.dto.bgaming.LayoutData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PlayerServiceImpl implements IPlayerService {

    @Override
    public void savePlayerData(Player player) {
        if (player != null) {
            log.info("userId：{}, time out", player.getUser().getUserID());
        }
    }

    @Override
    public void restorePlayerData(Player player, JSONObject gameInfo) {
        JSONObject options = gameInfo.getJSONObject("options");
        options.put("layout", LayoutData.builder().reels(5).rows(3).build());
        options.put("feature_options", LotteryConfig.FEATURE_OPTIONS);
        options.put("digit_options", LotteryConfig.DIGIT_OPTIONS);

        options.put("lines", LotteryConfig.PRIZE_LINE_OUT);
        options.put("paytable", LotteryConfig.PAYTABLE);
        options.put("paytables", LotteryConfig.PAYTABLES);
        options.put("reels", LotteryConfig.REELS);
        options.put("screen", LotteryConfig.SCREEN);
        options.put("special_symbols", LotteryConfig.SPECIAL_SYMBOLS);
        log.info("userId {} ,login Data {}", player.getUserId(), gameInfo.toJSONString());
    }

}
