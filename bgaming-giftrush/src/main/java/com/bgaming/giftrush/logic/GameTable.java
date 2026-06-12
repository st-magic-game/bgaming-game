package com.bgaming.giftrush.logic;

import com.alibaba.fastjson.JSONObject;

import com.bgaming.giftrush.entity.client.*;
import com.bgaming.giftrush.entity.log.RoundDetailDto;
import com.game.base.common.constant.GameKey;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.context.GameContext;
import com.game.base.domain.detail.DetailData;
import com.game.base.domain.game.Table;
import com.game.base.domain.game.TableSink;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.GameInfo;
import com.game.base.interfaces.dto.UsePrize;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static com.bgaming.giftrush.logic.GiftRushContext.*;
import static com.game.base.common.constant.GameKey.*;

@Slf4j
public class GameTable extends TableSink {

    private ApiClientResult result;

    public GameTable(GameInfo gameInfo, Table table) {
        super(gameInfo, table);
    }

    @Override
    public int getCoolTime() {
        return 50;
    }

    @Override
    public double getWinGold() {
        return result.getOutcome().getWin().doubleValue() / SUB_UNITS;
    }

    @Override
    public JSONObject codeResultData(Player player, double betScore, double factor) {
        return null;
    }
    public JSONObject codeResultData(Player player, double betScore, double factor,boolean bonus_buy) {
        result = generateApiResult(betScore, factor, bonus_buy);
        Flow flow = new Flow();
        result.setFlow(flow);
        if (bonus_buy) {
            flow.setPurchased_feature(new BuyBonus());
        }
        String pOrder = player.getUser().getUserID() + "-" + TimeUtil.getNow();
        String lastOrder = pOrder + "_1";
        flow.setRound_id(pOrder).setLast_action_id(lastOrder);
        double stake = betScore;
        if (bonus_buy) {
            stake = betScore * GLOBAL_CONFIG.getBonusBuy() / GLOBAL_CONFIG.getBaseBet();
        }
        result.setBalance(new Balance(result.getOutcome().getWin(),DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS - stake)));
        return JSONObject.parseObject(JSONObject.toJSONString(result));
    }

    @Override
    public double getCapacity(Player player, double betScore) {
        return 0;
    }

    @Override
    public JSONObject codeLogData(Player player, GameInfo roomInfo) {
        return null;
    }
    public JSONObject codeLogData(Player player, GameInfo roomInfo,double beforeScore,double betScore) {
        JSONObject jsonObject = new JSONObject(true);
        jsonObject.put(ICON_DATA, JSONObject.toJSONString(generateRoundDetail(result,beforeScore,player,betScore)));
        jsonObject.put(UUID, TimeUtil.getNow());
        jsonObject.put(BET_MUL, 1);
        jsonObject.put(PARENT_ORDER, result.getFlow().getRound_id());
        return jsonObject;
    }

    @Override
    public void changeUi(Player player, String data) {

    }

    @Override
    public Object startGame(Player player, String data) {
        try {
            JSONObject jData = JSONObject.parseObject(data);
            JSONObject options = jData.getJSONObject(OPTIONS);
            int userId = player.getUserId();
            Double stake = options.getDouble(BET);
            boolean bonus_buy = options.containsKey("purchased_feature") && options.getString("purchased_feature").equals("bonus_buy");

            if (environmentCheck(player, userId)) return null;

            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            if (cheatingDetection(player, stake)) return null;

            double orderStake = stake;
            if (bonus_buy) {
                orderStake = stake / GLOBAL_CONFIG.getBaseBet() * GLOBAL_CONFIG.getBonusBuy();
            }
            double realStake = DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue();
            double realOrderStake = DecimalUtil.getBigDecimal2(orderStake / SUB_UNITS).doubleValue();
            double beforeScore = player.getUser().getScore();
            if (notEnoughGold(realOrderStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, realStake, realOrderStake);
                return null;
            }
            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {} , buyFree {} ", player.getUser().getUserID(), stake, orderStake);
                return null;
            }

            this.lastStartTime = TimeUtil.getNow();
            double factor = GameContext.nextDouble(player,realStake);
            double winGold;
            JSONObject apiResult;
            int recount = 0;
            do {
                if (recount++ > 3) {
                    factor = 0.02;
                }
                apiResult = this.codeResultData(player, stake, factor, bonus_buy);
                winGold = this.getWinGold();
            } while (winGold - realOrderStake > 0 && reset(realStake, winGold, player, 10, 300, 3, 100));

            player.getUser().setBankScore(realOrderStake);
            GameContext.newGold(player, realStake, realOrderStake, winGold);
            if (realOrderStake > player.getUser().getScore()) {
                realOrderStake = player.getUser().getScore();
            }
            double changeScore = winGold - realOrderStake;
            setControlScore(player, changeScore);
            setCurData(player, realOrderStake, winGold);
            JSONObject extendData = getExtendData(bonus_buy);
            sendServerMsg(player, beforeScore, realOrderStake, winGold, null, extendData);
            log.info("玩家 {}  数据 result {}", player.getUserId(), apiResult);
            return apiResult;
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }

    @Override
    public void usePrize(Player player, UsePrize usePrize) {

    }

    private JSONObject getExtendData(boolean bonusBuy) {
        JSONObject jsonObject = new JSONObject();
        if(!result.getOutcome().getSpecial_symbols().getScatter().isEmpty() && result.getOutcome().getSpecial_symbols().getScatter().get(8).size() == 3) {
            jsonObject.put("isFree",true);
        }
        if (bonusBuy) {
            jsonObject.put("freeType",1);
        }
        jsonObject.put("pOrder", result.getFlow().getRound_id());
        return jsonObject;
    }


    private boolean environmentCheck(Player player, int userid) {
        if (checkDSScore(player)) {
            return true;
        }
        if (!isCooling()) {
            log.info("userid = {},cooling.....", userid);
            return true;
        }
        return false;
    }

    private static boolean cheatingDetection(Player player, Double stake) {
        if (stake < 0) {
            log.error("user {} , 作弊检测篡改数据!!! betScore {}", player.getUser().getUserID(), stake);
            return true;
        }
        return false;
    }

    private boolean notEnoughGold(double betScore, double beforeScore) {
        return betScore > DecimalUtil.getBigDecimal2(beforeScore).doubleValue();
    }

    private void sendServerMsg(Player player, double beforeScore, double betScore, double winGold, UsePrize u, JSONObject extData) {
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        player.initBetId(gameInfo.getRoomID(),pOrder );
        player.setBetIdNum(0);
        this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                betScore, winGold, 0, pOrder, extData, 1, u != null);
        log.info("userid = {},发送完整注单", player.getUser().getUserID());
        sendDataLog(player,betScore,beforeScore);
    }

    private void sendDataLog(Player player,double betScore,double beforeScore) {
        JSONObject jsonObject = codeLogData(player, gameInfo,beforeScore,betScore);
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        sendLogData(player, beforeScore, betScore, getWinGold(), pOrder, 1, jsonObject, betScore);
    }

    public Object getGameLogDetail(String data, Player player) {
        JSONObject jb = JSONObject.parseObject(data);
        Map<String, Object> map = new HashMap<>();
        map.put(GameKey.USER_ID, player.getUser().getUserID());
        map.put(GameKey.GAME_CODE, table.getGameInfo().getGameCode());
        map.put(GameKey.MERCHANT_ID, player.getUser().getMerchantId());
        if (jb.containsKey(GameKey.GAME_DATA)) {
            map.put(GameKey.GAME_DATA, jb.getString(GameKey.GAME_DATA));
        }
        if (jb.containsKey(GameKey.ROW_ID)) {
            map.put(GameKey.ROW_ID, jb.getString(GameKey.ROW_ID));
        }
        JSONObject jsonObject = table.getGameService().requestRecord(getRecord()[1], map);
        log.info("userid = {},orderDetail => {}", player.getUser().getUserID(), jsonObject);

        return parseOrderDetailLog(jsonObject,jb.getString(GameKey.ROW_ID));
    }
    private Object parseOrderDetailLog(JSONObject jsonObject,String refNo) {
        try {
            int code = jsonObject.getInteger("code");
            if (code == 200) {
                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject jDetails = data.getJSONObject("details");
                JSONObject extData = jDetails.getJSONObject("extData");
                String logData = extData.getString("iconData");
                return JSONObject.parseObject(logData, RoundDetailDto.class);
            }

        } catch (Exception e) {
            log.error("rep record error", e);
        }
        return new JSONObject();
    }

}
