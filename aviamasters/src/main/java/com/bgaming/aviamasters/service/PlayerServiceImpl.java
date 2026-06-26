package com.bgaming.aviamasters.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.aviamasters.entity.client.ApiClientResult;
import com.game.base.application.service.IPlayerService;
import com.game.base.domain.player.Player;
import com.game.base.interfaces.dto.bgaming.LayoutData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
        options.put("layout", LayoutData.builder().reels(1).rows(1).build());
        options.put("default_seed",37063712);
        options.put("lines", new int[]{});
        options.put("paytable",JSONObject.toJSONString("{}"));
        options.put("paytables",JSONObject.toJSONString("{}"));
        options.put("reels", JSONObject.toJSONString("{\"main\":[]}"));
        options.put("screen", new int[]{});
        options.put("special_symbols",new int[]{});
        log.info("userId {} ,login Data {}", player.getUserId(), gameInfo.toJSONString());
    }

}
