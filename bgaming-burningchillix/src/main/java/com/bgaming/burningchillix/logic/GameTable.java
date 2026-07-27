package com.bgaming.burningchillix.logic;

import com.alibaba.fastjson.JSONObject;

import com.bgaming.burningchillix.entity.client.ApiClientResult;
import com.bgaming.burningchillix.entity.client.Balance;
import com.game.base.common.constant.GameKey;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.context.GameContext;

import com.game.base.domain.game.Table;
import com.game.base.domain.game.TableSink;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.GameInfo;
import com.game.base.interfaces.dto.UsePrize;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


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
        return result.getOutcome().getWin().doubleValue() / BurningChilliXContext.SUB_UNITS;
    }

    @Override
    public JSONObject codeResultData(Player player, double betScore, double factor) {
        return null;
    }
    public void codeResultData(Player player, double betScore, double factor, int mode,double realStake) {
        String pOrder = nextId();
        result = BurningChilliXContext.generateApiResult(player, betScore, factor, mode, pOrder, realStake);
    }

    @Override
    public double getCapacity(Player player, double betScore) {
        return 0;
    }

    @Override
    public JSONObject codeLogData(Player player, GameInfo roomInfo) {
        return null;
    }

    @Override
    public void changeUi(Player player, String data) {

    }


    @Override
    public Object startGame(Player player, String data) {
        try {
            int userId = player.getUserId();
            if (environmentCheck(player, userId)) return null;

            JSONObject jData = JSONObject.parseObject(data);
            JSONObject options = null;
            if (jData.containsKey(OPTIONS)) {
                options = jData.getJSONObject(OPTIONS);
            }
            double stake;
            double realStake;
            double orderStake;
            int mode;
            if (options != null && !options.isEmpty()) {
                stake = options.getDouble(BET);
                mode = options.getInteger("mode");
            } else {
                return null;
            }
            if (cheatingDetection(player, stake)) return null;
            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}", player.getUser().getUserID(), stake);
                return null;
            }
            stake = DecimalUtil.getBigDecimal2(stake * mode / 20).doubleValue();
            orderStake = DecimalUtil.getBigDecimal2(stake / BurningChilliXContext.SUB_UNITS).doubleValue();

            realStake = DecimalUtil.getBigDecimal2(stake / BurningChilliXContext.SUB_UNITS).doubleValue();

            double beforeScore = player.getUser().getScore();
            if (notEnoughGold(realStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, realStake, realStake);
                return null;
            }

            this.lastStartTime = TimeUtil.getNow();
            double factor = GameContext.nextDouble(player,orderStake);
            double winGold;
            int recount = 0;
            do {
                if (recount++ > 3) {
                    factor = 0.02;
                }
                player.getExtendJson().clear();
                this.codeResultData(player, stake, factor,mode,realStake);
                winGold = this.getWinGold();
            } while (winGold - realStake > 0 && reset(realStake, winGold, player, 10, 500, 3, 300));

            player.getUser().setBankScore(stake);
            GameContext.newGold(player, orderStake, winGold);
            if (realStake > player.getUser().getScore()) {
                realStake = player.getUser().getScore();
            }
            double changeScore = winGold - realStake;
            setControlScore(player, changeScore);
            setCurData(player, realStake, winGold);
            JSONObject extendData = getExtendData();
            sendServerMsg(player, beforeScore, realStake, winGold,extendData);
            log.info("玩家 {}  数据 result {}", player.getUserId(), result);
            return JSONObject.parseObject(JSONObject.toJSONString(result));

        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }



    @Override
    public void usePrize(Player player, UsePrize usePrize) {

    }

    private JSONObject getExtendData() {
        JSONObject jsonObject = new JSONObject();
        if(result.getFeatures() != null && !result.getFeatures().isEmpty()) {
            jsonObject.put("isFree",true);
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

    private void sendServerMsg(Player player, double beforeScore, double betScore,double winGold,JSONObject extData) {
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        player.initBetId(gameInfo.getRoomID(),pOrder);
        player.setBetIdNum(1);
        this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                betScore, winGold, 0, pOrder, extData, 1, false);
        log.info("userid = {},发送完整注单", player.getUser().getUserID());
        sendDataLog(player,betScore,beforeScore,betScore);
    }



    private void sendDataLog(Player player,double stockScore,double beforeScore,double realStake) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ICON_DATA, JSONObject.toJSONString(BurningChilliXContext.generateRoundDetail(result,beforeScore,player,realStake)));
        jsonObject.put(UUID, TimeUtil.getNow());
        jsonObject.put(BET_MUL, 1);
        jsonObject.put(PARENT_ORDER, result.getFlow().getRound_id());
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        sendLogData(player, beforeScore, realStake, getWinGold(), pOrder, 1, jsonObject,stockScore);
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
    private JSONObject parseOrderDetailLog(JSONObject jsonObject,String refNo) {
        try {
            int code = jsonObject.getInteger("code");
            if (code == 200) {
                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject jDetails = data.getJSONObject("details");
                JSONObject extData = jDetails.getJSONObject("extData");
                JSONObject logData = extData.getJSONObject("iconData");
                logData.put("refNo",refNo);
                return logData;
            }

        } catch (Exception e) {
            log.error("rep record error", e);
        }
        return new JSONObject();
    }
}
