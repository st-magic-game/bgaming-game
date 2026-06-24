package com.bgaming.alienfruits2.logic;

import com.alibaba.fastjson.JSONObject;

import com.bgaming.alienfruits2.entity.client.ApiClientResult;
import com.bgaming.alienfruits2.entity.client.Balance;
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


import static com.bgaming.alienfruits2.logic.AlienFruits2Context.*;
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
        return result.getOutcome().getWin().doubleValue() / AlienFruits2Context.SUB_UNITS;
    }

    @Override
    public JSONObject codeResultData(Player player, double betScore, double factor) {
        return null;
    }
    public void codeResultData(Player player, double betScore, double factor, boolean bonus_buy, boolean freespin_chance,boolean inFree) {
        String pOrder = nextId();
        List<ApiClientResult> apiClientResults = generateApiResult(player, betScore, factor, bonus_buy, freespin_chance, pOrder);
        result = apiClientResults.get(apiClientResults.size() - 1);
        double stake = player.getExtendData("realStake", Double.class);

        AtomicReference<Double> totalScore = new AtomicReference<>((double) 0);
        apiClientResults.forEach(a -> totalScore.updateAndGet(v -> v + a.getOutcome().getWin().doubleValue()));
        double beforeScore = player.getUser().getScore();
        if (player.extendDataContainsKey("beforeScore")) {
            beforeScore = player.getExtendData("beforeScore", Double.class);
        }
        BigDecimal balance = DecimalUtil.getBigDecimal2((beforeScore  - stake) * AlienFruits2Context.SUB_UNITS);
        if (inFree) {
            balance = DecimalUtil.getBigDecimal2(player.getUser().getScore() * SUB_UNITS);
        }
        result.setBalance(new Balance(DecimalUtil.getBigDecimal2(totalScore.get()),balance));
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
        List<ApiClientResult> clientResults = new ArrayList<>();
        clientResults.add(result);
        jsonObject.put(ICON_DATA, JSONObject.toJSONString(generateRoundDetail(clientResults,beforeScore,player,betScore)));
        jsonObject.put(UUID, TimeUtil.getNow());
        jsonObject.put(BET_MUL, 1);
        jsonObject.put(PARENT_ORDER, result.getFlow().getRound_id());
        return jsonObject;
    }

    @Override
    public void changeUi(Player player, String data) {

    }

    private boolean checkInFree(Player player) {
        if (player.extendDataContainsKey("freeNum") && player.getEFreeNum() > 0) {
            int totalFreeNum = player.getExtendData("totalFreeNum",Integer.class);
            int freeNum = player.getEFreeNum();
            log.info("玩家：{} 当前在在免费场中，总免费次数：{}，剩余免费次数： {}",player.getUser().getUserID(),totalFreeNum,freeNum);
            return true;
        }
        return false;
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
            double realStake = 0;
            double orderStake;
            boolean bonus_buy = false;
            boolean freespin_chance = false;
            boolean inFree = false;
            int freeNum = 0;
            int totalFreeNum = 0;
            List<ApiClientResult> apiClientResults = new ArrayList<>();
            if (options != null && !options.isEmpty()) {
                if (checkInFree(player)) {
                    log.error("userid = {},作弊，当前玩家在免费场中，不行进行新的游戏",userId);
                    return null;
                }
                stake = options.getDouble(BET);
                bonus_buy = options.containsKey("purchased_feature") && options.getString("purchased_feature").equals("freespin_buy");
                freespin_chance = options.containsKey("purchased_feature") && options.getString("purchased_feature").equals("freespin_chance");
                if (bonus_buy && freespin_chance) {
                    log.error("userid = {},作弊，玩家篡改客户端，不能同时买免费和增加免费机会",userId);
                    return null;
                }
            } else {
                if (!checkInFree(player)) {
                    log.error("userid = {},作弊，玩家当前不在免费场中",userId);
                    return null;
                }
                stake = player.getUser().getBankScore();
                inFree = true;
                freeNum = player.getEFreeNum();
                totalFreeNum = player.getExtendData("totalFreeNum",Integer.class);
                apiClientResults = player.getExtendDataList("apiClient",ApiClientResult.class);
            }
            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            orderStake = DecimalUtil.getBigDecimal2(stake / AlienFruits2Context.SUB_UNITS).doubleValue();
            if (cheatingDetection(player, stake)) return null;
            String bonusUby = "No";
            if (!inFree) {
                realStake = DecimalUtil.getBigDecimal2(stake / AlienFruits2Context.SUB_UNITS).doubleValue();
                if (bonus_buy) {
                    realStake = DecimalUtil.getBigDecimal2(stake / GLOBAL_CONFIG.getBaseBet() * GLOBAL_CONFIG.getFreeSpinBuy() / AlienFruits2Context.SUB_UNITS).doubleValue();
                    bonusUby = "Freespin buy";
                }
                if (freespin_chance) {
                    realStake = DecimalUtil.getBigDecimal2(stake / GLOBAL_CONFIG.getBaseBet() * GLOBAL_CONFIG.getBonusBuy() / AlienFruits2Context.SUB_UNITS).doubleValue();
                    bonusUby = "Freespin chance";
                }
            }

            double beforeScore = player.getUser().getScore();
            if (notEnoughGold(realStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, realStake, realStake);
                return null;
            }
            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {} , buyFree {} ", player.getUser().getUserID(), stake, realStake);
                return null;
            }
            if (!inFree) {
                player.getExtendJson().clear();
                player.setExtendData("beforeScore",beforeScore);
                player.setExtendData("realStake",realStake);
                player.setExtendData("bonusBuy",bonusUby);
            }

            this.lastStartTime = TimeUtil.getNow();
            double factor = GameContext.nextDouble(player,orderStake);
            if (inFree) {
                factor *= 2;
            }
            if (freespin_chance) {
                factor *= 1.25;
            }
            double winGold;
            int recount = 0;
            do {
                if (recount++ > 3) {
                    factor = 0.02;
                }
                player.setExtendData("freeNum",freeNum);
                player.getExtendJson().put("apiClient",apiClientResults);
                player.setExtendData("totalFreeNum",totalFreeNum);
                this.codeResultData(player, stake, factor,bonus_buy,freespin_chance,inFree);
                winGold = this.getWinGold();
            } while (winGold - realStake > 0 && reset(orderStake, winGold, player, 10, 300, 3, 100));

            player.getUser().setBankScore(stake);
            GameContext.newGold(player, orderStake, realStake, winGold);
            if (realStake > player.getUser().getScore()) {
                realStake = player.getUser().getScore();
            }
            double changeScore = winGold - realStake;
            setControlScore(player, changeScore);
            JSONObject extendData = getExtendData(bonus_buy);
            sendServerMsg(player, beforeScore, realStake, null, extendData);
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

    private JSONObject getExtendData(boolean bonusBuy) {
        JSONObject jsonObject = new JSONObject();
        if(result.getFeatures() != null && !result.getFeatures().isEmpty()) {
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

    private void sendServerMsg(Player player, double beforeScore, double betScore, UsePrize u, JSONObject extData) {
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        player.initBetId(gameInfo.getRoomID(),pOrder);
        boolean finish = result.getFlow().getState().equals("closed");
        if (betScore > 0) {
            setCurData(player, betScore, 0);
            player.setBetIdNum(0);
            this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                    betScore, 0, 0, pOrder, extData, 0, u != null);
        }
        if (finish) {
            List<ApiClientResult> apiClientResults = player.getExtendDataList("apiClient",ApiClientResult.class);
            AtomicReference<Double> winGold = new AtomicReference<>((double) 0);
            apiClientResults.forEach(a -> winGold.updateAndGet(v -> v + a.getOutcome().getWin().doubleValue()));
            setCurData(player, 0, DecimalUtil.getBigDecimal2(winGold.get() / SUB_UNITS).doubleValue());
            player.setBetIdNum(1);
            this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                    0, DecimalUtil.getBigDecimal2(winGold.get() / SUB_UNITS).doubleValue(), 1, pOrder, extData, 1, u != null);
            log.info("userid = {},发送完整注单", player.getUser().getUserID());
            double stockScore = DecimalUtil.getBigDecimal2(player.getUser().getBankScore() / SUB_UNITS).doubleValue();
            sendDataLog(player,stockScore);
        }
//        sendSingleDataLog(player,betScore,beforeScore,stockScore);
    }

    private void sendSingleDataLog(Player player,double betScore,double beforeScore,double stockScore) {
        JSONObject jsonObject = codeLogData(player, gameInfo,beforeScore,betScore);
        String pOrder = String.valueOf(result.getFlow().getRound_id());
        sendLogData(player, beforeScore, betScore, getWinGold(), pOrder, 0, jsonObject, stockScore);
    }

    private void sendDataLog(Player player,double stockScore) {
        double beforeScore = DecimalUtil.getBigDecimal2(player.getExtendData("beforeScore", Double.class)).doubleValue();
        double realStake = DecimalUtil.getBigDecimal2(player.getExtendData("realStake", Double.class)).doubleValue();

        JSONObject jsonObject = new JSONObject();
        List<ApiClientResult> apiClientResults = player.getExtendDataList("apiClient",ApiClientResult.class);
        jsonObject.put(ICON_DATA, JSONObject.toJSONString(generateRoundDetail(apiClientResults,beforeScore,player,realStake)));
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
