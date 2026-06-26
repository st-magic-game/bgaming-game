package com.bgaming.aviamasters.logic;

import com.alibaba.fastjson.JSONObject;

import com.bgaming.aviamasters.entity.client.ApiClientResult;
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

import java.util.HashMap;
import java.util.Map;


import static com.bgaming.aviamasters.logic.AviamastersContext.*;
import static com.game.base.common.constant.GameKey.*;

@Slf4j
public class GameTable extends TableSink {

    private AviamastersRoundModeFinder.RoundProcessResult result;

    public GameTable(GameInfo gameInfo, Table table) {
        super(gameInfo, table);
    }

    @Override
    public int getCoolTime() {
        return 50;
    }

    @Override
    public double getWinGold() {
        return DecimalUtil.getBigDecimal2((double) result.getWin() / SUB_UNITS).doubleValue();
    }

    @Override
    public JSONObject codeResultData(Player player, double betScore, double factor) {
        return null;
    }


    @Override
    public double getCapacity(Player player, double betScore) {
        return 0;
    }

    @Override
    public JSONObject codeLogData(Player player, GameInfo roomInfo) {
        JSONObject jsonObject = new JSONObject(true);
        jsonObject.put(ICON_DATA, JSONObject.toJSONString(generateRoundDetail(player,result)));
        jsonObject.put(UUID, TimeUtil.getNow());
        jsonObject.put(BET_MUL, 1);
        jsonObject.put(PARENT_ORDER, result.getPOrder());
        return jsonObject;
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
            long stake = 0;
            double realStake = 0;
            if (options != null && !options.isEmpty()) {
                stake = options.getLong(BET);
            } else {
                log.error("userid = {},作弊，玩家没有传options",userId);
            }
            if (cheatingDetection(player, stake)) return null;
            realStake = DecimalUtil.getBigDecimal2((double) stake / SUB_UNITS).doubleValue();

            double beforeScore = player.getUser().getScore();
            if (notEnoughGold(realStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, realStake, realStake);
                return null;
            }
            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {} , buyFree {} ", player.getUser().getUserID(), stake, realStake);
                return null;
            }

            this.lastStartTime = TimeUtil.getNow();
            double factor = GameContext.nextDouble(player,realStake);

            double winGold;
            int recount = 0;
            String pOrder = nextId();
            do {
                if (recount++ > 3) {
                    factor = 0.02;
                }
                result = roundProcessResult(stake,factor,pOrder);
                winGold = this.getWinGold();
            } while (winGold - realStake > 0 && reset(realStake, winGold, player, 10, 300, 3, 100));

            player.getUser().setBankScore(stake);
            GameContext.newGold(player, realStake, realStake, winGold);
            if (realStake > player.getUser().getScore()) {
                realStake = player.getUser().getScore();
            }
            ApiClientResult apiClientResult = generateResult(player, result);
            double changeScore = winGold - realStake;
            setControlScore(player, changeScore);
            setCurData(player, realStake, winGold);
            sendServerMsg(player, beforeScore, realStake, winGold, null,new JSONObject());
            log.info("userid = {},sourceData = {},clientData = {}", player.getUserId(), JSONObject.toJSONString(result),JSONObject.toJSONString(apiClientResult));
            return JSONObject.parseObject(JSONObject.toJSONString(apiClientResult));
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }



    @Override
    public void usePrize(Player player, UsePrize usePrize) {

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

    private static boolean cheatingDetection(Player player, long stake) {
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
        String pOrder = result.getPOrder();
        player.initBetId(gameInfo.getRoomID(),pOrder);
        this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                betScore, winGold, 0, pOrder, extData, 1, u != null);
        double stockScore = DecimalUtil.getBigDecimal2(player.getUser().getBankScore() / SUB_UNITS).doubleValue();
        log.info("userid = {},发送完整注单", player.getUser().getUserID());
        sendDataLog(player,stockScore,beforeScore,betScore);
    }


    private void sendDataLog(Player player,double stockScore,double beforeScore,double realStake) {
        JSONObject jsonObject = codeLogData(player, gameInfo);
        String pOrder = result.getPOrder();
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
