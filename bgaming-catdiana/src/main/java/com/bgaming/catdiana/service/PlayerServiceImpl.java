package com.bgaming.catdiana.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.catdiana.entity.PlayerAdditionalInformation;
import com.bgaming.catdiana.entity.Scene;
import com.bgaming.catdiana.entity.dto.SpinResponse;
import com.bgaming.catdiana.mapper.PlayerAdditionalInformationMapper;
import com.game.base.application.service.IPlayerService;
import com.game.base.common.config.BetConfig;
import com.game.base.common.config.BetScore;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

import static com.bgaming.catdiana.config.LotteryConfig.SUB_UNITS;
import static com.game.base.common.constant.GameKey.SCENE;

@Slf4j
@Service
public class PlayerServiceImpl implements IPlayerService {
    private final PlayerAdditionalInformationMapper playerAdditionalInformationMapper;

    @Autowired
    private BetConfig betConfig;

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
                    String scenesStr = JSONArray.toJSONString(scenes);
                    PlayerAdditionalInformation pai = new PlayerAdditionalInformation();
                    pai.setScenes(scenesStr);
                    pai.setUserId(player.getUserId());
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
        try {
            gameInfo.clear();
            String optionsStr = "{\"line_bets\":[1,2,3,4,5,6,8,10,20,30,40,50,60,80,100,200,300,400],\"default_bet\":5,\"scatter_bet_factors\":{\"3\":1,\"4\":1,\"5\":1},\"paytable\":{\"[\\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\"]\":500,\"[\\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\"]\":250,\"[\\\"elvis\\\", \\\"elvis\\\", \\\"elvis\\\"]\":25,\"[\\\"girl\\\", \\\"girl\\\", \\\"girl\\\", \\\"girl\\\", \\\"girl\\\"]\":500,\"[\\\"girl\\\", \\\"girl\\\", \\\"girl\\\", \\\"girl\\\"]\":250,\"[\\\"girl\\\", \\\"girl\\\", \\\"girl\\\"]\":25,\"[\\\"limo\\\", \\\"limo\\\", \\\"limo\\\", \\\"limo\\\", \\\"limo\\\"]\":400,\"[\\\"limo\\\", \\\"limo\\\", \\\"limo\\\", \\\"limo\\\"]\":150,\"[\\\"limo\\\", \\\"limo\\\", \\\"limo\\\"]\":20,\"[\\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\"]\":300,\"[\\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\"]\":100,\"[\\\"guitar\\\", \\\"guitar\\\", \\\"guitar\\\"]\":15,\"[\\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\"]\":200,\"[\\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\"]\":50,\"[\\\"microphone\\\", \\\"microphone\\\", \\\"microphone\\\"]\":10,\"[\\\"a\\\", \\\"a\\\", \\\"a\\\", \\\"a\\\", \\\"a\\\"]\":50,\"[\\\"a\\\", \\\"a\\\", \\\"a\\\", \\\"a\\\"]\":20,\"[\\\"a\\\", \\\"a\\\", \\\"a\\\"]\":10,\"[\\\"k\\\", \\\"k\\\", \\\"k\\\", \\\"k\\\", \\\"k\\\"]\":50,\"[\\\"k\\\", \\\"k\\\", \\\"k\\\", \\\"k\\\"]\":20,\"[\\\"k\\\", \\\"k\\\", \\\"k\\\"]\":5,\"[\\\"q\\\", \\\"q\\\", \\\"q\\\", \\\"q\\\", \\\"q\\\"]\":50,\"[\\\"q\\\", \\\"q\\\", \\\"q\\\", \\\"q\\\"]\":20,\"[\\\"q\\\", \\\"q\\\", \\\"q\\\"]\":5,\"[\\\"j\\\", \\\"j\\\", \\\"j\\\", \\\"j\\\", \\\"j\\\"]\":50,\"[\\\"j\\\", \\\"j\\\", \\\"j\\\", \\\"j\\\"]\":20,\"[\\\"j\\\", \\\"j\\\", \\\"j\\\"]\":5},\"lines\":[[1,1,1,1,1],[0,0,0,0,0],[2,2,2,2,2],[0,1,2,1,0],[2,1,0,1,2],[1,0,0,0,1],[1,2,2,2,1],[0,0,1,2,2],[2,2,1,0,0],[1,2,1,0,1],[1,0,1,2,1],[0,1,1,1,0],[2,1,1,1,2],[0,1,0,1,0],[2,1,2,1,2],[1,1,0,1,1],[1,1,2,1,1],[0,0,2,0,0],[2,2,0,2,2],[0,2,2,2,0],[2,0,0,0,2],[1,2,0,2,1],[1,0,2,0,1],[0,2,0,2,0],[2,0,2,0,2]],\"reels\":[[\"elvis\",\"elvis\",\"elvis\",\"k\",\"a\",\"limo\",\"q\",\"k\",\"guitar\",\"star\",\"a\",\"k\",\"guitar\",\"q\",\"a\",\"guitar\",\"k\",\"limo\",\"j\",\"star\",\"microphone\",\"j\",\"girl\",\"microphone\",\"k\",\"j\",\"q\",\"microphone\",\"k\",\"guitar\",\"microphone\",\"k\",\"a\",\"microphone\",\"q\",\"a\",\"microphone\",\"q\",\"k\",\"coin\",\"coin\",\"coin\",\"q\",\"a\",\"k\",\"j\",\"girl\",\"star\",\"k\",\"q\",\"guitar\",\"a\"],[\"elvis\",\"elvis\",\"elvis\",\"limo\",\"a\",\"q\",\"a\",\"j\",\"k\",\"microphone\",\"q\",\"j\",\"k\",\"a\",\"q\",\"j\",\"a\",\"q\",\"j\",\"k\",\"a\",\"microphone\",\"limo\",\"j\",\"microphone\",\"k\",\"guitar\",\"j\",\"k\",\"limo\",\"guitar\",\"q\",\"j\",\"girl\",\"q\",\"guitar\",\"a\",\"k\",\"limo\",\"q\",\"coin\",\"coin\",\"coin\",\"guitar\",\"j\",\"k\",\"girl\",\"j\",\"a\",\"guitar\",\"q\",\"microphone\",\"a\"],[\"elvis\",\"elvis\",\"elvis\",\"k\",\"k\",\"j\",\"q\",\"j\",\"girl\",\"k\",\"j\",\"star\",\"girl\",\"a\",\"q\",\"guitar\",\"k\",\"a\",\"q\",\"limo\",\"k\",\"j\",\"microphone\",\"k\",\"j\",\"q\",\"k\",\"guitar\",\"j\",\"limo\",\"q\",\"j\",\"girl\",\"limo\",\"j\",\"q\",\"guitar\",\"j\",\"a\",\"k\",\"microphone\",\"coin\",\"coin\",\"coin\",\"j\",\"guitar\",\"limo\",\"j\",\"star\",\"q\",\"microphone\",\"guitar\",\"q\",\"k\"],[\"elvis\",\"elvis\",\"elvis\",\"k\",\"j\",\"q\",\"a\",\"microphone\",\"k\",\"j\",\"girl\",\"microphone\",\"a\",\"q\",\"guitar\",\"a\",\"microphone\",\"q\",\"limo\",\"a\",\"j\",\"limo\",\"microphone\",\"j\",\"guitar\",\"girl\",\"q\",\"guitar\",\"microphone\",\"limo\",\"k\",\"girl\",\"q\",\"guitar\",\"limo\",\"girl\",\"k\",\"a\",\"q\",\"guitar\",\"coin\",\"coin\",\"a\",\"j\",\"k\",\"a\",\"guitar\",\"k\",\"microphone\",\"q\",\"a\",\"j\"],[\"elvis\",\"elvis\",\"elvis\",\"girl\",\"j\",\"q\",\"girl\",\"microphone\",\"guitar\",\"a\",\"j\",\"star\",\"q\",\"a\",\"guitar\",\"star\",\"q\",\"k\",\"guitar\",\"a\",\"j\",\"limo\",\"q\",\"j\",\"limo\",\"microphone\",\"q\",\"j\",\"k\",\"q\",\"j\",\"limo\",\"q\",\"j\",\"microphone\",\"q\",\"star\",\"guitar\",\"girl\",\"coin\",\"coin\",\"coin\",\"guitar\",\"j\",\"k\",\"limo\",\"j\",\"star\",\"microphone\",\"girl\",\"a\",\"q\"]],\"freespin_reels\":[[\"star\",\"a\",\"microphone\",\"limo\",\"coin\",\"elvis\",\"elvis\",\"elvis\",\"guitar\",\"j\",\"q\",\"j\",\"a\",\"k\",\"j\",\"a\",\"q\",\"microphone\",\"j\",\"k\",\"limo\",\"guitar\",\"a\",\"q\",\"microphone\",\"a\",\"k\",\"microphone\",\"a\",\"q\",\"limo\",\"k\",\"guitar\",\"a\",\"q\",\"limo\",\"j\",\"a\",\"q\",\"k\",\"guitar\",\"a\",\"q\",\"girl\",\"guitar\",\"a\",\"q\",\"limo\",\"guitar\",\"j\",\"q\",\"guitar\",\"limo\",\"q\",\"guitar\",\"a\",\"q\",\"k\",\"guitar\",\"a\",\"j\",\"limo\",\"a\",\"guitar\",\"microphone\",\"j\"],[\"elvis\"],[\"coin\",\"elvis\",\"girl\",\"q\",\"k\",\"a\",\"j\",\"guitar\",\"q\",\"microphone\",\"a\",\"q\",\"k\",\"j\",\"a\",\"k\",\"limo\",\"guitar\",\"k\",\"j\",\"j\",\"k\",\"j\",\"limo\",\"microphone\",\"k\",\"j\",\"limo\",\"k\",\"a\",\"guitar\",\"q\",\"a\",\"q\",\"k\",\"j\",\"star\",\"k\",\"j\",\"q\",\"k\",\"j\",\"q\",\"k\",\"j\",\"q\",\"k\",\"j\",\"q\",\"microphone\",\"j\",\"q\",\"k\",\"microphone\",\"q\",\"a\",\"j\",\"q\",\"a\",\"girl\",\"q\",\"k\",\"j\",\"guitar\",\"a\"],[\"elvis\"],[\"coin\",\"elvis\",\"elvis\",\"elvis\",\"microphone\",\"a\",\"q\",\"microphone\",\"k\",\"guitar\",\"q\",\"k\",\"j\",\"q\",\"microphone\",\"k\",\"j\",\"guitar\",\"q\",\"a\",\"guitar\",\"k\",\"microphone\",\"star\",\"limo\",\"a\",\"q\",\"k\",\"j\",\"q\",\"a\",\"j\",\"k\",\"microphone\",\"a\",\"limo\",\"j\",\"girl\",\"k\",\"a\",\"j\",\"q\",\"a\",\"girl\",\"j\",\"a\",\"q\",\"microphone\",\"guitar\",\"j\",\"limo\",\"q\",\"k\",\"j\",\"a\",\"q\",\"a\",\"j\",\"a\",\"microphone\",\"k\",\"q\",\"j\",\"guitar\",\"microphone\"]],\"column_height\":3,\"currency\":{\"code\":\"FUN\",\"symbol\":\"FUN\",\"subunits\":100,\"exponent\":2},\"digit_options\":{\"bet\":2,\"paytable\":2,\"balance\":2},\"coin_values\":[1,2,3,4,5,6,7,8,10,14,16,18,20,24,30,100],\"jackpots\":{\"mini\":30,\"major\":100,\"mega\":1000}}";
            JSONObject newOptions = JSONObject.parseObject(optionsStr);
            BetScore betScore = betConfig.getBetMaps().get(player.getUser().getCoinsType().trim());
            List<BigDecimal> baseBet = betScore.getBaseBet();
            newOptions.put("line_bets",baseBet);
            newOptions.put("default_bet",betScore.getDefaultBt());
            gameInfo.put("options", newOptions);
            gameInfo.put("game", JSONObject.parseObject("{\"state\":\"idle\"}"));
            gameInfo.put("balance", DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS));
            gameInfo.put("available_commands", JSONArray.parseArray("[\"init\",\"spin\"]"));
            if (player.getExtendJson().containsKey("spinResponse")) {
                SpinResponse spinResponse = (SpinResponse) player.getExtendJson().get("spinResponse");
                JSONObject previous_lines = new JSONObject();
                previous_lines.put("previous_lines", spinResponse.getBets().getJSONObject("lines"));
                gameInfo.put("bets", previous_lines);
                gameInfo.put("game", spinResponse.getGame());
                gameInfo.put("result", spinResponse.getResult());
            } else {
                PlayerAdditionalInformation additionalInformation = playerAdditionalInformationMapper.getAdditionalInformation(player.getUserId());
                if (additionalInformation != null) {
                    String scenes = additionalInformation.getScenes();
                    String lastUi = additionalInformation.getLastUi();
                    if (StringUtils.hasText(lastUi) && StringUtils.hasText(scenes)) {
                        List<Scene> sceneList = JSONArray.parseArray(scenes, Scene.class);
                        SpinResponse spinResponse = JSONObject.parseObject(lastUi, SpinResponse.class);
                        JSONObject previous_lines = new JSONObject();
                        JSONObject bet;
                        if (spinResponse.getBets().containsKey("lines")) {
                            bet = spinResponse.getBets().getJSONObject("lines");
                        } else {
                            bet = spinResponse.getBets().getJSONObject("previous_lines");
                        }
                        previous_lines.put("previous_lines", bet);
                        gameInfo.put("bets", previous_lines);
                        gameInfo.put("game", spinResponse.getGame());
                        gameInfo.put("result", spinResponse.getResult());
                        player.getExtendJson().put(SCENE, sceneList);
                        player.getExtendJson().put("spinResponse", spinResponse);
                        additionalInformation.setScenes(new JSONArray().toJSONString());
                        additionalInformation.setLastUi(new JSONObject().toJSONString());
                        playerAdditionalInformationMapper.upsertLastUiByUserId(additionalInformation);
                    }
                }
            }
            log.info("userId {} ,login Data {}", player.getUserId(), gameInfo.toJSONString());
        } catch (Exception e) {
            log.error("userId {} . restorePlayerData error:", player.getUserId(), e);
        }
    }

}
