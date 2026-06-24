package com.bgaming.alienfruits2.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.alienfruits2.entity.PlayerAdditionalInformation;
import com.bgaming.alienfruits2.entity.client.ApiClientResult;
import com.bgaming.alienfruits2.entity.client.Balance;
import com.bgaming.alienfruits2.entity.client.Outcome;
import com.bgaming.alienfruits2.logic.AlienFruits2Context;
import com.bgaming.alienfruits2.mapper.PlayerAdditionalInformationMapper;
import com.game.base.application.service.IPlayerService;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.player.Player;
import com.game.base.interfaces.dto.bgaming.LayoutData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.bgaming.alienfruits2.logic.AlienFruits2Context.*;

@Slf4j
@Service
public class PlayerServiceImpl implements IPlayerService {

    private static final String REELS = "";

    private static final String SCREEN = "[[\"1\",\"1\",\"0\",\"4\",\"4\"],[\"7\",\"9\",\"9\",\"6\",\"6\"],[\"6\",\"4\",\"4\",\"9\",\"9\"],[\"7\",\"6\",\"6\",\"4\",\"4\"],[\"6\",\"2\",\"2\",\"8\",\"8\"],[\"7\",\"5\",\"5\",\"8\",\"8\"]]";

    private static final String SPECIAL_PAY_TABLE = "{\"[:scatter,\\\"0\\\"]\":{\"4\":3,\"5\":5,\"6\":100}}";

    @Resource
    private PlayerAdditionalInformationMapper mapper;

    @Override
    public void savePlayerData(Player player) {
        if (player != null) {
            List<ApiClientResult> apiClientResults = new ArrayList<>();
            int freeNum = 0;
            int totalFreeNum = 0;
            String usedFeature = "No";
            double beforeScore = player.getUser().getScore();
            if (player.extendDataContainsKey("apiClient")) {
                apiClientResults = player.getExtendDataList("apiClient", ApiClientResult.class);
            }
            if (player.extendDataContainsKey("freeNum")) {
                freeNum = player.getEFreeNum();
            }
            if (player.extendDataContainsKey("totalFreeNum")) {
                totalFreeNum = player.getExtendData("totalFreeNum",Integer.class);
            }
            if (player.extendDataContainsKey("bonusBuy")) {
                usedFeature = player.getExtendData("bonusBuy", String.class);
            }
            if (player.extendDataContainsKey("beforeScore")) {
                beforeScore = player.getExtendData("beforeScore", Double.class);
            }
            double stake = player.getUser().getBankScore();
            PlayerAdditionalInformation pai = new PlayerAdditionalInformation();
            pai.setUserId(player.getUserId()).setLastUi(JSONObject.toJSONString(apiClientResults))
                            .setBetScore(stake).setFreeNum(freeNum).setTotalFreeNum(totalFreeNum)
                            .setUsedFeature(usedFeature).setBeforeScore(beforeScore)
                            .setUpdateTime(TimeUtil.getNow());
            int result = mapper.upsertLastUiByUserId(pai);
            log.info("userId = {}, saveData = {}.result = {}", player.getUser().getUserID(),JSONObject.toJSONString(pai),result);
        }
    }

    @Override
    public void restorePlayerData(Player player, JSONObject gameInfo) {
        JSONObject options = gameInfo.getJSONObject("options");
        options.put("layout", LayoutData.builder().reels(6).rows(5).build());
        options.put("paytable", getPayTable());
        options.put("paytable_levels", null);
        options.put("screen", JSONArray.parse(SCREEN));
        options.put("feature_options", getFeatureOptions());
        options.put("special_symbols", getSpecialSymbols());
        options.put("special_paytable",JSONObject.parseObject(SPECIAL_PAY_TABLE));
//        gameInfo.put("options",JSONObject.parseObject(test));

//        JSONObject currency = options.getJSONObject("currency");
//        currency.put("code","FUN");
//        currency.put("symbol","FUN");
        restoreData(player,gameInfo);
    }

    private void restoreData(Player player, JSONObject gameInfo) {
        if (player.extendDataContainsKey("apiClient")) {
            List<ApiClientResult> apiClient = player.getExtendDataList("apiClient", ApiClientResult.class);
            if (!apiClient.isEmpty()) {
                ApiClientResult apiClientResult = apiClient.get(apiClient.size() - 1);
                if (!apiClientResult.getFlow().getState().equals("closed")) {
                    gameInfo.put("outcome",apiClientResult.getOutcome());
                    player.setExtendData("beforeScore",DecimalUtil.getBigDecimal2(player.getUser().getScore()));
                }
                if (apiClientResult.getFeatures() != null) {
                    Integer leftNum = apiClientResult.getFeatures().getInteger("freespins_left");
                    if (leftNum > 0) {
                        gameInfo.put("features",apiClientResult.getFeatures());
                    }
                }
                AtomicReference<Double> totalScore = new AtomicReference<>((double) 0);
                apiClient.forEach(a -> totalScore.updateAndGet(v -> v + a.getOutcome().getWin().doubleValue()));
                Balance balance = new Balance(DecimalUtil.getBigDecimal2(totalScore.get()),DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS));
                gameInfo.put("balance",balance);

            }
        } else {
            PlayerAdditionalInformation pai = mapper.getAdditionalInformation(player.getUserId());
            if (pai != null) {
                String lastUi = pai.getLastUi();
                List<ApiClientResult> apiClientResults = JSONArray.parseArray(lastUi, ApiClientResult.class);
                player.setExtendData("apiClient",apiClientResults);
                player.setEFreeNum(pai.getFreeNum());
                player.setExtendData("totalFreeNum",pai.getTotalFreeNum());
                player.setExtendData("bonusBuy",pai.getUsedFeature());
                player.setExtendData("beforeScore",DecimalUtil.getBigDecimal2(player.getUser().getScore()));
                if (!apiClientResults.isEmpty()) {
                    ApiClientResult apiClientResult = apiClientResults.get(apiClientResults.size() - 1);
                    if (!apiClientResult.getFlow().getState().equals("closed")) {
                        gameInfo.put("outcome",apiClientResult.getOutcome());
                        AtomicReference<Double> totalScore = new AtomicReference<>((double) 0);
                        apiClientResults.forEach(a -> totalScore.updateAndGet(v -> v + a.getOutcome().getWin().doubleValue()));
                        Balance balance = new Balance(DecimalUtil.getBigDecimal2(totalScore.get()),DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS));
                        gameInfo.put("balance",balance);
                    }
                    if (apiClientResult.getFeatures() != null) {
                        Integer leftNum = apiClientResult.getFeatures().getInteger("freespins_left");
                        if (leftNum > 0) {
                            gameInfo.put("features",apiClientResult.getFeatures());
                        }
                    }
                }
                log.info("userid = {},restoreData = {}", player.getUserId(), JSONObject.toJSONString(pai));
            } else {
                log.info("userid = {},No restoreData", player.getUserId());
            }
        }


    }

}
