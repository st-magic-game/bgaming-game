package com.bgaming.candymonsta.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.candymonsta.config.LotteryConfig;
import com.bgaming.candymonsta.entity.PlayerAdditionalInformation;
import com.bgaming.candymonsta.entity.Scene;
import com.bgaming.candymonsta.entity.dto.SpinResponse;
import com.bgaming.candymonsta.mapper.PlayerAdditionalInformationMapper;
import com.game.base.application.service.IPlayerService;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import com.game.base.interfaces.dto.bgaming.LayoutData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.game.base.common.constant.GameKey.SCENE;

@Slf4j
@Service
public class PlayerServiceImpl implements IPlayerService {
    private final PlayerAdditionalInformationMapper playerAdditionalInformationMapper;

    @Autowired
    public PlayerServiceImpl(PlayerAdditionalInformationMapper playerAdditionalInformationMapper) {
        this.playerAdditionalInformationMapper = playerAdditionalInformationMapper;
    }

    @Override
    public void savePlayerData(Player player) {
        if (player != null) {
            log.info("userId：{}, time out", player.getUser().getUserID());
            try {
                if (player.getExtendJson().containsKey(SCENE)) {
                    List<Scene> scenes = (List<Scene>) player.getExtendJson().get(SCENE);
                    int eTimes = player.getETimes();
                    String scenesStr = JSONArray.toJSONString(scenes);
                    PlayerAdditionalInformation pai = new PlayerAdditionalInformation();
                    pai.setScenes(scenesStr);
                    pai.setUserId(player.getUserId());
                    pai.setPlayTimes(eTimes);
                    pai.setUpdateTime(TimeUtil.getNow());
                    pai.setBetScore(scenes.get(0).getBetScore());
                    if (player.getExtendJson().containsKey("spinResponse")) {
                        SpinResponse response = (SpinResponse) player.getExtendJson().get("spinResponse");
                        pai.setLastUi(JSONObject.toJSONString(response));
                    }
                    int result = playerAdditionalInformationMapper.upsertLastUiByUserId(pai);
                    log.info("userId：{}, time out, save data {} result {}", player.getUser().getUserID(), JSONObject.toJSONString(pai), result);
                }
            } catch (Exception e) {
                log.error("save error userId {} .e:", player.getUserId(), e);
                log.error("userId {} . error data {}", player.getUserId(), JSONObject.toJSONString(player.getExtendJson().get(SCENE)));
            }
        }
    }

    @Override
    public void restorePlayerData(Player player, JSONObject gameInfo) {
        JSONObject options = gameInfo.getJSONObject("options");
        options.put("layout", LayoutData.builder().reels(5).rows(3).build());
        options.put("lines", LotteryConfig.PRIZE_LINE_OUT);
        options.put("paytable", LotteryConfig.PAYTABLE);
        options.put("paytables", LotteryConfig.PAYTABLES);
        options.put("reels", LotteryConfig.REELS);
        options.put("screen", LotteryConfig.SCREEN);
        options.put("special_symbols", LotteryConfig.SPECIAL_SYMBOLS);
        if (player.getExtendJson().containsKey("spinResponse")) {
            SpinResponse spinResponse = (SpinResponse) player.getExtendJson().get("spinResponse");
            setBackUiData(player, gameInfo, spinResponse);
        }else{
            PlayerAdditionalInformation additionalInformation = playerAdditionalInformationMapper.getAdditionalInformation(player.getUserId());
            if(additionalInformation != null){
                String scenes = additionalInformation.getScenes();
                String lastUi = additionalInformation.getLastUi();
                if (StringUtils.hasText(lastUi) && StringUtils.hasText(scenes)) {
                    List<Scene> sceneList = JSONArray.parseArray(scenes, Scene.class);
                    SpinResponse spinResponse = JSONObject.parseObject(lastUi, SpinResponse.class);
                    setBackUiData(player, gameInfo, spinResponse);
                    player.getExtendJson().put(SCENE,sceneList);
                    player.setETimes(additionalInformation.getPlayTimes());
                    player.getExtendJson().put("spinResponse",spinResponse);
                    additionalInformation.setScenes(new JSONArray().toJSONString());
                    additionalInformation.setLastUi(new JSONObject().toJSONString());
                    playerAdditionalInformationMapper.upsertLastUiByUserId(additionalInformation);
                }
            }
        }
        log.info("userId {} ,login Data {}", player.getUserId(), gameInfo.toJSONString());
    }

    private static void setBackUiData(Player player, JSONObject gameInfo, SpinResponse spinResponse) {
        if (spinResponse != null) {
            spinResponse.getBalance().setWallet(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
            String jsonString = JSONObject.toJSONString(spinResponse.getFlow());
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            jsonObject.put("command", "init");
            gameInfo.put("flow", jsonObject);
            gameInfo.put("features", spinResponse.getFeatures());
            gameInfo.put("balance", spinResponse.getBalance());
            gameInfo.put("outcome", spinResponse.getOutcome());
        }
    }

}
