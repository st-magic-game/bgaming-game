package com.bgaming.totallyhot.service;

import com.alibaba.fastjson.JSONObject;
import com.game.base.application.service.IPlayerService;
import com.game.base.domain.player.Player;
import com.game.base.interfaces.dto.bgaming.LayoutData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.bgaming.totallyhot.config.LotteryConfig.*;

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
        options.put("lines", PRIZE_LINE_OUT);
        options.put("paytable", PAYTABLE);
        options.put("paytables", PAYTABLES);
        options.put("reels", REELS);
        options.put("screen", SCREEN);
        options.put("special_symbols", SPECIAL_SYMBOLS);
        log.info("userId {} ,login Data {}", player.getUserId(), gameInfo.toJSONString());
    }

}
