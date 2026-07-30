package com.bgaming.pandaluck.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.pandaluck.config.LotteryConfig;
import com.bgaming.pandaluck.entity.BonusData;
import com.bgaming.pandaluck.entity.PrizeIcon;
import com.bgaming.pandaluck.entity.Scene;
import com.bgaming.pandaluck.entity.dto.OutCome;
import com.bgaming.pandaluck.entity.dto.RoundDetailDto;
import com.bgaming.pandaluck.entity.dto.SpinResponse;
import com.bgaming.pandaluck.utils.DateTimeUtil;
import com.game.base.common.constant.GameKey;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.RandomUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.context.GameContext;
import com.game.base.domain.game.Table;
import com.game.base.domain.game.TableSink;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.GameInfo;
import com.game.base.interfaces.dto.UsePrize;
import com.game.base.interfaces.dto.bgaming.BgBalance;
import com.game.base.interfaces.dto.bgaming.FlowData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

import static com.bgaming.pandaluck.config.LotteryConfig.*;
import static com.game.base.common.constant.GameKey.*;
import static com.game.base.common.constant.Protocol.BGAMING_COMMAND_SPIN;
import static com.game.base.common.constant.Protocol.BGAMING_STATE_CLOSED;

/**
 * 跳高高逻辑类
 */
@Slf4j
public class GameTable extends TableSink {

    public GameTable(GameInfo roomInfo, Table table) {
        super(roomInfo, table);
    }

    /**
     * 当局中奖金币
     */
    private double totalWinGold;

    public Object startGame(Player player, String data) {
        try {
            JSONObject jData = JSONObject.parseObject(data);
            JSONObject options = jData.getJSONObject(OPTIONS);
            String command = jData.getString("command");
            int userId = player.getUserId();
            double beforeScore = player.getUser().getScore();
            if (environmentCheck(player, userId)) return null;

            Double stake;
            List<Scene> scenes = getScenes(player);
            int times = player.getETimes();
            if (command.equals("respin")) {
                if (scenes == null || scenes.isEmpty() || scenes.size() == 1 || times + 1 >= scenes.size()) {
                    log.error("userId {} , error request freeSpin1, scene == null or normal scenes", userId);
                    return null;
                }
                stake = scenes.get(0).getBetScoreServer() * LotteryConfig.SUB_UNITS;
                times++;
                SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue(), beforeScore, times);
                player.setETimes(times);
                log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                return response;
            }

            stake = options.getDouble(BET);
            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            if (cheatingDetection(player, stake)) return null;

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}  ", player.getUser().getUserID(), stake);
                return null;
            }

            int requestType = 0;
            if (options.containsKey(LotteryConfig.PURCHASED_FEATURE)) {
                String purchasedFeature = options.getString(LotteryConfig.PURCHASED_FEATURE);
                if (purchasedFeature.equals(LotteryConfig.PURCHASED_BONUS_SPIN)) {
                    requestType = 1;
                }
            }

            stake = DecimalUtil.getBigDecimal2(stake / LotteryConfig.SUB_UNITS).doubleValue();
            double orderStake = DecimalUtil.getBigDecimal2(stake * LotteryConfig.REQUEST_TYPE_MUL[requestType]).doubleValue();

            if (notEnoughGold(orderStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, stake, orderStake);
                return null;
            }

            checkAndSetBuyFree(player, requestType);
            this.lastStartTime = TimeUtil.getNow();
            player.getExtendJson().put(BET_MUL, 1);
            int betType = getBetType(player);
            double factor = GameContext.nextDouble(player, stake);
            double winGold;
            int recount = 0;
            do {
                this.totalWinGold = 0;
                if (recount++ > 3) {
                    factor = 0.02;
                }
                this.codeResultData(player, stake, factor);
                winGold = this.getWinGold();
                if (betType > 0) {
                    player.getExtendJson().put("buyFree", 1);
                }
            } while (winGold - orderStake > 0 && reset(stake, winGold, player, 10, 300, 3, 100));

            player.getUser().setBankScore(stake);
            GameContext.newGold(player, stake, orderStake, winGold);
            if (orderStake > player.getUser().getScore()) {
                orderStake = player.getUser().getScore();
            }
            scenes = getScenes(player);
            double changeScore = winGold - orderStake;
            setControlScore(player, changeScore);
            resetPlayerExt(player);
            Scene scene = scenes.get(0);
            scene.setBetScore(DecimalUtil.getBigDecimal2(orderStake).doubleValue());
            scene.setFreeType(requestType);
            scene.setType(requestType);
            scene.setBetScoreServer(DecimalUtil.getBigDecimal2(stake).doubleValue());
            player.initBetId(gameInfo.getRoomID(), scene.getOrder());
            player.setBetIdNum(0);
            SpinResponse response = getSpinResponse(player, orderStake, scenes, stake, beforeScore, 0);
            log.info("玩家 {}  数据 result {}", player.getUserId(), JSONObject.toJSONString(response));
            return response;
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }

    private JSONObject getExtendData(Player player, Scene scene, boolean finish) {
        JSONObject extendData = getExtendString(player, scene.getPOrder());
        extendData.put(FREE_TYPE, scene.getFreeType());
        extendData.put(BUY_TYPE, 0);
        extendData.put(LotteryConfig.BET_TYPE, 0);
        extendData.put("lastOrder", finish);
        return extendData;
    }

    private SpinResponse getSpinResponse(Player player, double orderStake, List<Scene> scenes, Double stake, double beforeScore, int times) {
        Scene scene = scenes.get(times);
        boolean finish = scene.isFinish();
        if (orderStake > 0) {
            double winGold = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
            boolean lastOrder = finish && winGold == 0;
            JSONObject extendData = getExtendData(player, scenes.get(0), lastOrder);
            setCurData(player, orderStake, 0);
            this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                    orderStake, 0, 0, scenes.get(0).getPOrder(), extendData, lastOrder ? 1 : 0, false);
        }
        scene.setBetScore(DecimalUtil.getBigDecimal2(orderStake).doubleValue());
        scene.setBetScoreServer(DecimalUtil.getBigDecimal2(stake).doubleValue());
        scene.setBeforeScore(DecimalUtil.getBigDecimal2(beforeScore).doubleValue());
        if (times > 0) {
            String pOrder = scenes.get(0).getPOrder();
            String order = scenes.get(0).getOrder();
            scene.setOrder(order);
            scene.setAfterScore(DecimalUtil.getBigDecimal2(player.getUser().getScore()).doubleValue());
            scene.setPOrder(pOrder);
        }
        SpinResponse response = generateResponse(scenes.subList(0, times + 1), DecimalUtil.getBigDecimal2(player.getUser().getScore()).doubleValue());
        List<RoundDetailDto> roundDetailDtos = new ArrayList<>();
        if (finish) {
            boolean buyBonus = scenes.get(0).getFreeType() == 1;
            boolean first = true;
            for (Scene tmpScene : scenes) {
                RoundDetailDto roundDetailDto = generateRoundDetail(tmpScene.getBeforeScore(), player, tmpScene);
                if (!first) {
                    roundDetailDto.setBonusScene(true);
                }
                if (buyBonus) {
                    roundDetailDto.setScatterWin(BigDecimal.ONE); //标记而已 并非真实赢分
                    roundDetailDto.setFeatureName(featureNameByBetType(1));
                }
                roundDetailDtos.add(roundDetailDto);
                first = false;
            }
            JSONObject extendData = getExtendData(player, scenes.get(0), true);
            double winGold = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
            sendServerMsg(player, winGold, roundDetailDtos, extendData);
        }
        player.getExtendJson().put("spinResponse", response);
        if (finish) {
            player.getExtendJson().remove(SCENE);
            player.getExtendJson().remove("spinResponse");
        }
        return response;
    }

    private RoundDetailDto generateRoundDetail(double beforeScore, Player player, Scene scene) {
        int[][] rotary = scene.getRotary();
        List<PrizeIcon> prizeDetail = scene.getPrizeDetail();
        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));
        roundDetailDto.setUsedFeature(scene.getFreeType() == 1);
        roundDetailDto.setFeatureName(featureNameByBetType(scene.getFreeType()));
        BigDecimal realBet = DecimalUtil.getBigDecimal2(scene.getBetScore());
        BigDecimal realWin = DecimalUtil.getBigDecimal2(scene.getGold());
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(scene.getGold() - scene.getBetScore());
        roundDetailDto.setBetText(realBet.toPlainString());
        roundDetailDto.setBet(realBet);
        roundDetailDto.setTotalWinText(realWin.toPlainString());
        roundDetailDto.setTotalWin(realWin);
        roundDetailDto.setProfitText(realProfit.toPlainString());
        roundDetailDto.setProfit(realProfit);
        roundDetailDto.setCurrency(player.getCoinsType());
        roundDetailDto.setBalanceBeforeText(DecimalUtil.getBigDecimal2(beforeScore).toPlainString());
        roundDetailDto.setBalanceBefore(DecimalUtil.getBigDecimal2(beforeScore));
        roundDetailDto.setBalanceAfterText(DecimalUtil.getBigDecimal2(player.getUser().getScore()).toPlainString());
        roundDetailDto.setBalanceAfter(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
        roundDetailDto.setWinLines(castDetailWinLine(prizeDetail));
        roundDetailDto.setBaseBetText(DecimalUtil.getBigDecimal2(scene.getBetScoreServer()).stripTrailingZeros().toPlainString());
        roundDetailDto.setSymbols(castDetailSymbol(rotary));
        if (scene.getBonusData() != null) {
            roundDetailDto.setBonusData(scene.getBonusData());
        }
        return roundDetailDto;
    }

    private String featureNameByBetType(int betType) {
        if (betType == 1) return "Bonus buy";

        return "No";
    }

    private List<List<String>> castDetailWinLine(List<PrizeIcon> prizeDetail) {
        List<List<String>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == LotteryConfig.SCATTER) continue;

            String line = String.valueOf(prizeIcon.getLine()).concat("x");
            String iconStr = SYMBOL_NAME[prizeIcon.getIcon()];
            String lineIdEndPos = "Line ".concat(String.valueOf(prizeIcon.getHitLine() + 1)).concat(" - ").concat(prizeIcon.getGold().stripTrailingZeros().toPlainString());
            List<String> winLine = Arrays.asList(line, iconStr, lineIdEndPos);
            result.add(winLine);
        }
        return result;
    }

    private List<String> castDetailSymbol(int[][] rotary) {
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < LotteryConfig.ROWS; i++) {
            for (int i1 = 0; i1 < LotteryConfig.COLUMNS; i1++) {
                int icon = rotary[i1][i];
                symbols.add(SYMBOL_NAME[icon]);
            }
        }
        return symbols;
    }

    private static final String[] SYMBOL_NAME = {"bonus", "h1", "h2", "h3", "l1", "l2", "l3", "l4", "l5"};

    private void resetPlayerExt(Player player) {
        player.getExtendJson().remove("buyFree");
        player.getExtendJson().remove("playTimes");
        player.getExtendJson().remove(LotteryConfig.BET_TYPE);
    }

    public SpinResponse generateResponse(List<Scene> scenes, double betAfterScore) {
        Scene firstScene = scenes.get(0);
        Scene scene = scenes.get(scenes.size() - 1);
        Double totalWin = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
        double betScore = firstScene.getBetScoreServer();
        String orderId = firstScene.getOrder();
        int orderSer = scene.getNumber() + 1;
        BgBalance balance = new BgBalance();
        balance.setGame(DecimalUtil.getBigDecimal2(totalWin * LotteryConfig.SUB_UNITS));
        balance.setWallet(DecimalUtil.getBigDecimal2(betAfterScore * LotteryConfig.SUB_UNITS));

        FlowData flowData = this.table.getGameService().initFlowData();
        flowData.setState(BGAMING_STATE_CLOSED);
        flowData.setCommand(BGAMING_COMMAND_SPIN);
        flowData.setRound_id(orderId);
        flowData.setLast_action_id(orderId + "_" + orderSer);
        OutCome outCome = new OutCome();
        outCome.setBet(DecimalUtil.getBigDecimal2(betScore * LotteryConfig.SUB_UNITS));
        outCome.setWin(DecimalUtil.getBigDecimal2(scene.getGold() * LotteryConfig.SUB_UNITS));
        outCome.setSpecial_symbols(checkSpecialSymbol(scene.getRotary()));
        outCome.setWins(checkWins(scene.getPrizeDetail()));
        outCome.setScreen(castReel(scene.getRotary()));

        SpinResponse gameResponse = new SpinResponse();
        gameResponse.setApi_version(this.table.getGameService().getBaseVersion().getApiVersion());
        gameResponse.setBalance(balance);
        if (firstScene.getFreeType() == 1) {
            String jsonString = JSONObject.toJSONString(flowData);
            JSONObject inner = new JSONObject();
            inner.put("name", "bonus_buy");
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            jsonObject.put("purchased_feature", inner);
            gameResponse.setFlow(jsonObject);
        } else {
            gameResponse.setFlow(flowData);
        }
        gameResponse.setOutcome(outCome);
        if (scene.getBonusData() != null) {
            JSONObject bonusData = new JSONObject();
            bonusData.put("bonus_data", scene.getBonusData());
            gameResponse.setFeatures(bonusData);
        }
        return gameResponse;
    }

    private List<List<String>> castReel(int[][] rotary) {
        List<List<String>> screen = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            int icon1 = rotary[i][0];
            int icon2 = rotary[i][1];
            int icon3 = rotary[i][2];
            List<String> list = new ArrayList<>();
            list.add(String.valueOf(icon1));
            list.add(String.valueOf(icon2));
            list.add(String.valueOf(icon3));
            screen.add(list);
        }
        return screen;
    }

    private List<List<Object>> checkWins(List<PrizeIcon> prizeDetail) {
        List<List<Object>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            List<Object> win = new ArrayList<>();
            if (prizeIcon.getIcon() == LotteryConfig.SCATTER) continue;

            win.add("line");
            win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(LotteryConfig.SUB_UNITS)));
            win.add(castColumnIdx(prizeIcon.getPrizeIndex()));
            win.add(prizeIcon.getHitLine());
            result.add(win);
        }
        return result;
    }

    private int[] castColumnIdx(Set<Integer> prizeIndex) {
        int[] winIndex = new int[prizeIndex.size()];
        for (Integer idx : prizeIndex) {
            winIndex[idx % LotteryConfig.COLUMNS] = idx / LotteryConfig.COLUMNS;
        }
        return winIndex;
    }

    private JSONObject checkSpecialSymbol(int[][] rotary) {
        JSONObject result = new JSONObject();
        List<List<Integer>> indexes = new ArrayList<>();
        for (int i = 0; i < LotteryConfig.ROWS; i++) {
            for (int i1 = 0; i1 < LotteryConfig.COLUMNS; i1++) {
                int icon = rotary[i][i1];
                if (icon == LotteryConfig.SCATTER) {
                    List<Integer> index = new ArrayList<>();
                    index.add(i1);
                    index.add(i);
                    indexes.add(index);
                }
            }
        }
        if (!indexes.isEmpty()) {
            Map<Integer, List<List<Integer>>> inner = new HashMap<>();
            inner.put(0, indexes);
            result.put("scatter", inner);
        }
        return result;
    }

    @Override
    public void usePrize(Player player, UsePrize usePrize) {

    }

    private void sendServerMsg(Player player, double winGold, List<RoundDetailDto> gameDetail, JSONObject extData) {
        List<Scene> scenes = getScenes(player);
        double afterBetScore = scenes.get(0).getAfterScore();
        player.initBetId(gameInfo.getRoomID(), scenes.get(0).getOrder());
        player.setBetIdNum(1);
        if (winGold > 0) {
            setCurData(player, 0, winGold);
            this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(afterBetScore).doubleValue(), this.gameInfo,
                    0, winGold, 1, scenes.get(0).getPOrder(), extData, 1, false);
            RoundDetailDto lastRoundDetail = gameDetail.get(gameDetail.size() - 1);
            BigDecimal afterScore = DecimalUtil.getBigDecimal2(player.getUser().getScore());
            lastRoundDetail.setBalanceAfter(afterScore);
            lastRoundDetail.setBalanceAfterText(afterScore.stripTrailingZeros().toPlainString());
        }
        log.info("userid = {},发送完整注单", player.getUser().getUserID());
        sendDataLog(player, gameDetail, winGold);
    }

    private int getBetType(Player player) {
        int betType = 0;
        if (player.getExtendJson().containsKey(LotteryConfig.BET_TYPE)) {
            betType = player.getExtendJson().getInteger(LotteryConfig.BET_TYPE);
        }
        return betType;
    }

    private void checkAndSetBuyFree(Player player, Integer betOptionType) {
        if (betOptionType == 0) return;

        log.info("userId {} , set buyFree state = {} , set state success!!", player.getUser().getUserID(), betOptionType);
        player.getExtendJson().put(LotteryConfig.BET_TYPE, betOptionType);
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

    private static List<Scene> getScenes(Player player) {
        List<Scene> scenes = null;
        if (player.getExtendJson().containsKey("scene")) {
            scenes = (List<Scene>) player.getExtendJson().get("scene");
        }
        return scenes;
    }

    /**
     * 发送es日志
     *
     * @param player 当前玩家
     */
    private void sendDataLog(Player player, List<RoundDetailDto> gameDetail, double gold) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送es日志时，服务器发生错误");
            return;
        }
        double settleBet = scenes.get(0).getBetScore();
        double betScore = scenes.get(0).getBetScoreServer();
        String pOrder = scenes.get(0).getPOrder();

        BigDecimal beforeScore = DecimalUtil.getBigDecimal2(player.getUser().getScore() - gold + settleBet);
        BigDecimal afterScore = DecimalUtil.getBigDecimal2(player.getUser().getScore());
        gameDetail.get(0).setBalanceBefore(beforeScore);
        gameDetail.get(0).setBalanceBeforeText(beforeScore.stripTrailingZeros().toPlainString());
        gameDetail.get(gameDetail.size() - 1).setBalanceAfter(afterScore);
        gameDetail.get(gameDetail.size() - 1).setBalanceAfterText(afterScore.stripTrailingZeros().toPlainString());
        JSONObject jObj = new JSONObject();
        jObj.put(ICON_DATA, JSONObject.toJSONString(gameDetail));
        jObj.put(UUID, TimeUtil.getNow());
        jObj.put(BET_MUL, player.getEMul());
        jObj.put(PARENT_ORDER, pOrder);
        sendLogData(player, DecimalUtil.getBigDecimal2(player.getUser().getScore() - gold + settleBet).doubleValue(), settleBet, gold, pOrder, 1, jObj, betScore);
    }

    /**
     * 设置注单中的扩展数据
     *
     * @param player 当前玩家
     * @return 扩资数据
     */
    private JSONObject getExtendString(Player player, String pOrder) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送注单时，服务器发生错误");
            throw new RuntimeException("发送注单数据错误");
        }

        return this.getPrizeStatistics(scenes, pOrder);
    }

    /**
     * 历史记录中奖设计
     */
    private JSONObject getPrizeStatistics(List<Scene> scenes, String pOrder) {
        /* 历史记录中奖统计 */
        JSONObject prizeStatistics = new JSONObject();
        boolean isFree = scenes.stream().anyMatch(s -> s.getType() == 1);
        int dropNum = 0;//中免费奖之前掉落次数
        int freePrize = 0;//免费场中的中奖次数
        if (isFree) {
            boolean flag = true;
            for (Scene scene : scenes) {
                if (flag && scene.getType() == 0) {
                    dropNum++;
                } else {
                    flag = false;
                    if (scene.getGold() > 0) {
                        freePrize++;
                    }
                }
            }
            dropNum--;
        } else {
            dropNum = scenes.size() - 1;
        }
        prizeStatistics.put("isFree", isFree);
        prizeStatistics.put("dropNum", dropNum);
        prizeStatistics.put("freePrize", freePrize);
        prizeStatistics.put("pOrder", pOrder);

        return prizeStatistics;
    }

    private List<Scene> getResultScene(double betScore, double factor, Player gamePlayer) {
        int betType = getBetType(gamePlayer);
        long now = TimeUtil.getNow();
        String pOrder = gamePlayer.getUser().getUserID() + "-" + now++;
        gamePlayer.getExtendJson().put("pOrder", pOrder);
        List<Scene> sceneIconVos = new ArrayList<>();
        Scene scene = generatedScene(betType, betScore, factor);
        scene.setOrder(nextId(now));
        scene.setPOrder(pOrder);
        sceneIconVos.add(scene);
        List<Integer> scatterIndexes = checkScatterIndexes(scene);
        if (scatterIndexes.size() >= 3) {
            int[][] initRotary = copyRotary(scene.getRotary());
            BonusData bonusData = generateBonusData(factor, 3, initRotary, true);
            scene.setBonusData(bonusData);
            int respinCount = 3;
            while (respinCount > 0) {
                List<Integer> canInstallIndex = getCanInstallIndex(initRotary);
                if (canInstallIndex.isEmpty()) {
                    break;
                }

                respinCount--;
                bonusData = generateBonusData(factor, respinCount, initRotary, false);
                if (!bonusData.getNew_values().isEmpty()) {
                    respinCount = 3;
                    bonusData.setRespins_left(3);
                }
                scene = new Scene();
                scene.setBonusData(bonusData);
                scene.setType(1);
                fillRespinRotary(scene, initRotary);
                sceneIconVos.add(scene);
            }
            scene.setBonusWin(DecimalUtil.getBigDecimal2(betScore * bonusData.getMultiplier()));
            scene.setGold(scene.getBonusWin().doubleValue());
        }
        scene.setFinish(true);
        return sceneIconVos;
    }

    private void fillRespinRotary(Scene scene, int[][] rotary) {
        List<Integer> icons = new ArrayList<>(ALL_ICONS);
        BonusData bonusData = scene.getBonusData();
        int[][] coinsScreen = bonusData.getCoins_screen();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int flag = coinsScreen[i][i1];
                if (flag == 0) {
                    rotary[i][i1] = icons.remove(RandomUtil.nextInt(icons.size()));
                }
            }
        }
        scene.setRotary(copyRotary(rotary));
    }

    private int[][] copyRotary(int[][] rotary) {
        int[][] newRotary = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            newRotary[i] = Arrays.copyOf(rotary[i], COLUMNS);
        }
        return newRotary;
    }

    private BonusData generateBonusData(double factor, int left, int[][] initRotary, boolean first) {
        List<List<Object>> new_values = new ArrayList<>();
        int pandaSize = 0;
        int[][] coins_screen = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (initRotary[i][i1] != SCATTER) continue;

                pandaSize++;
                coins_screen[i][i1] = 1;
                if (first) {
                    int[] position = {i, i1};
                    List<Object> newVal = Arrays.asList("0", position, 1);
                    new_values.add(newVal);
                }
            }
        }

        if (!first) {
            int scatterSize = scatterSizeFactor(factor);
            if (scatterSize > pandaSize) {
                List<Integer> canInstallIndex = getCanInstallIndex(initRotary);
                int add = scatterSize - pandaSize;
                int size = Math.min(add, canInstallIndex.size());
                for (int i = 0; i < size; i++) {
                    if (RandomUtil.nextDouble() > 0.7 * factor) continue;

                    Integer installIndex = canInstallIndex.remove(RandomUtil.nextInt(canInstallIndex.size()));
                    int y = installIndex / COLUMNS;
                    int x = installIndex % COLUMNS;
                    initRotary[y][x] = SCATTER;
                    coins_screen[y][x] = 1;
                    pandaSize++;
                    int[] position = {y, x};
                    List<Object> newVal = Arrays.asList("0", position, 1);
                    new_values.add(newVal);
                }
            }
        }

        int mul = getMul(SCATTER, pandaSize);
        BonusData bonusData = new BonusData();
        bonusData.setRespins_left(left);
        bonusData.setMultiplier(mul);
        bonusData.setCoins_screen(coins_screen);
        bonusData.setNew_values(new_values);
        return bonusData;
    }

    private List<Integer> getCanInstallIndex(int[][] initRotary) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (initRotary[i][i1] == 0) continue;

                indexes.add(i * COLUMNS + i1);
            }
        }
        return indexes;
    }

    public static int scatterSizeFactor(double factor) {
        factor = Math.max(0.6, Math.min(2.0, factor));
        double[] pro;
        if (factor < 1) {
            double t = (1 - factor) / 0.4;
            t = Math.pow(t, 1.5);
            pro = lerp(BASE_PRO, LOW_PRO, t);
        } else {
            double t = (factor - 1);
            t = Math.pow(t, 1.8);
            pro = lerp(BASE_PRO, HIGH_PRO, t);
        }
        return randomByPro(pro);
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        double[] result = new double[a.length];
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + (b[i] - a[i]) * t;
            sum += result[i];
        }
        for (int i = 0; i < result.length; i++) {
            result[i] /= sum;
        }
        return result;
    }

    public static void main(String[] args) {
        test();
    }

    public static void test() {

        double total = 0;

        int[] mul = {
                3,
                10,
                20,
                50,
                100,
                500,
                2024
        };

        for (int i = 0; i < 1000000; i++) {

            int scatter = scatterSizeFactor(1);

            int win = mul[scatter - 3];

            total += win;
        }


        System.out.println(total / 1000000);
    }

    private static int randomByPro(double[] pro) {
        double r = RandomUtil.nextDouble();
        double cur = 0;
        for (int i = 0; i < pro.length; i++) {
            cur += pro[i];
            if (r <= cur) {
                return i + 3;
            }
        }
        return 3;
    }

    private List<Integer> checkScatterIndexes(Scene scene) {
        List<Integer> scatterIndex = new ArrayList<>();
        int[][] rotary = scene.getRotary();
        for (int i = 0; i < LotteryConfig.ROWS; i++) {
            for (int i1 = 0; i1 < LotteryConfig.COLUMNS; i1++) {
                if (rotary[i][i1] == LotteryConfig.SCATTER) {
                    scatterIndex.add(i * LotteryConfig.COLUMNS + i1);
                }
            }
        }
        return scatterIndex;
    }

    /**
     * 根据概率生成一些长的中奖线
     */
    private static void sceneLoneLines(int[][] rotary, double random) {
        double ran = random > 1.05 ? random : Math.pow(random, 2);
        if (RandomUtil.nextDouble() <= LotteryConfig.LONG_LINES_PRO * ran) {
            int num = RandomUtil.nextDouble() <= LotteryConfig.LONG_LINES_PRO * ran ? 2 : 1;
            List<List<Integer>> prizeLines = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                List<Integer> list = new ArrayList<>();
                for (int i1 = 0; i1 < LotteryConfig.COLUMNS; i1++) {
                    list.add(RandomUtil.nextInt(LotteryConfig.ROWS) * LotteryConfig.COLUMNS + i1);
                }
                prizeLines.add(list);
            }

            for (List<Integer> prizeLine : prizeLines) {
                //随机找一个图标
                int icon = LotteryConfig.ICONS_WITH_MULTIPLE[RandomUtil.nextInt(2, LotteryConfig.ICONS_WITH_MULTIPLE.length)];
                for (int i1 = 0; i1 < prizeLine.size(); i1++) {
                    if (i1 == 1) continue;

                    int index = prizeLine.get(i1);
                    rotary[index / LotteryConfig.COLUMNS][index % LotteryConfig.COLUMNS] = icon;
                }
            }
        }
    }

    /**
     * 生成场景
     */
    private static Scene generatedScene(int betType, double betScore, double factor) {
        Scene scene = new Scene();
        scene.setDoubleMul(1);
        int[][] rotary = getInitRotary();
        sceneLoneLines(rotary, factor);
        //随机填充1，2，4，5个转轴的图标
        for (int i = 0; i < LotteryConfig.COLUMNS; i++) {
            if (i == 1) continue;

            for (int i1 = 0; i1 < LotteryConfig.ROWS; i1++) {
                if (rotary[i1][i] == -1) {
                    int icon = LotteryConfig.getRandomNormalIcon();
                    rotary[i1][i] = icon;
                }
            }
        }
        int scatterSize = LotteryConfig.getScatterSize();
        if (factor <= 1) {
            scatterSize = Math.min(scatterSize, 3);
        }
        if (betType == 1) {
            scatterSize = 3;
            if (RandomUtil.nextDouble() < 0.05) {
                scatterSize += 1;
            }
        }
        setScatterIcon(rotary, scatterSize);
        List<Integer> useIcons = arrToList();
        for (int i = 0; i < LotteryConfig.ROWS; i++) {
            if (rotary[i][1] == -1) {
                Map<Integer, Integer> prizeMaps = getPrize(rotary, i * LotteryConfig.COLUMNS + 1);
                if (!prizeMaps.isEmpty()) {
                    List<Integer> icons = new ArrayList<>(prizeMaps.keySet());
                    Integer icon = icons.get(RandomUtil.nextInt(icons.size()));
                    double mul = prizeMaps.get(icon) * scene.getDoubleMul() * 1.0D / LotteryConfig.BASE_LINE;
                    double tmpRan = factor > 1 ? factor : Math.pow(factor, 3);
                    double tempFactor = tmpRan * LotteryConfig.SMALL_WIN_PRO;
                    if (mul >= 100) { // 不干预100以上倍数
                        tempFactor = tmpRan;
                    }
                    // 降低小奖概率
                    if (mul <= 1) {
                        tempFactor *= 0.83780182;
                    }
                    if (tempFactor / mul >= RandomUtil.nextDouble()) {
                        rotary[i][1] = icon;
                    }
                }
                if (rotary[i][1] == -1) {
                    List<Integer> temp = new ArrayList<>(useIcons);
                    if (!prizeMaps.isEmpty()) {
                        prizeMaps.keySet().forEach(temp::remove);
                    }
                    if (temp.isEmpty()) {//如果所有图标都有可能中奖 就随机给一个图标
                        temp = new ArrayList<>(useIcons);
                    }
                    rotary[i][1] = temp.get(RandomUtil.nextInt(temp.size()));
                }
            }
        }
        scene.setRotary(rotary);
        setMulWithScene(scene, betScore);
        return scene;
    }

    private static void setScatterIcon(int[][] rotary, int scatterSize) {
        if (scatterSize == 0) return;

        List<Integer> indexes = new ArrayList<>(INDEXES);
        for (int i = 0; i < scatterSize; i++) {
            Integer index = indexes.remove(RandomUtil.nextInt(indexes.size()));
            rotary[index / COLUMNS][index % COLUMNS] = LotteryConfig.SCATTER;
        }
    }


    /**
     * @return 返回所有的普通图标集合
     */
    private static List<Integer> arrToList() {
        List<Integer> list = new ArrayList<>();
        for (int i : LotteryConfig.ICONS_WITH_MULTIPLE) {
            list.add(i);
        }
        return list;
    }

    /**
     * 获取可能中奖的倍数
     *
     * @param rotary 棋盘
     * @param index  转轴位置
     * @return 返回可能中奖的图标
     */
    private static Map<Integer, Integer> getPrize(int[][] rotary, int index) {
        Map<Integer, Integer> prizeMaps = new HashMap<>();
        for (int i = 0; i < LotteryConfig.PRIZE_LINE.length; i++) {
            int[] prizeLine = LotteryConfig.PRIZE_LINE[i];
            for (int i1 : prizeLine) {
                if (i1 == index) {
                    int[] lineIcons = checkLineIcon(prizeLine, rotary);
                    PrizeIcon prizeIcon = checkPrize(lineIcons, prizeLine, i);
                    if (prizeIcon == null) continue;

                    int icon = prizeIcon.getIcon();
                    if (icon == SCATTER) continue;

                    int line = prizeIcon.getLine();
                    int mul = LotteryConfig.getMul(icon, line);
                    if (!prizeMaps.containsKey(icon)) {
                        prizeMaps.put(icon, mul);
                    } else {
                        prizeMaps.put(icon, prizeMaps.get(icon) + mul);
                    }
                    break;
                }
            }
        }
        return prizeMaps;
    }


    public static PrizeIcon checkPrize(int[] arr, int[] prizeLine, int hitLine) {
        Integer icon = null;
        int count = 0;
        for (int current : arr) {
            if (current == -1) {
                count++;
                continue;
            }
            if (icon == null) {
                icon = current;
                count++;
                continue;
            }
            if (icon == current) {
                count++;
            } else {
                break;
            }
        }
        if (icon != null && count >= 3 && icon != LotteryConfig.SCATTER) {
            Set<Integer> pos = new HashSet<>();
            for (int i = 0; i < count; i++) {
                pos.add(prizeLine[i]);
            }
            return new PrizeIcon(icon, hitLine, count, pos);
        }
        return null;
    }

    private static int[] checkLineIcon(int[] prizeLine, int[][] rotary) {
        int[] lineIcons = new int[LotteryConfig.COLUMNS];
        for (int i = 0; i < prizeLine.length; i++) {
            int index = prizeLine[i];
            int icon = rotary[index / LotteryConfig.COLUMNS][index % LotteryConfig.COLUMNS];
            lineIcons[i] = icon;
        }
        return lineIcons;
    }


    /**
     * @return 获取初始化转轴列表
     */
    private static int[][] getInitRotary() {
        int[][] rotary = new int[LotteryConfig.ROWS][LotteryConfig.COLUMNS];
        for (int[] ints : rotary) {
            Arrays.fill(ints, -1);
        }
        return rotary;
    }

    /**
     * 设置场景中奖倍数和中奖坐标
     *
     * @param scene 场景
     */
    private static void setMulWithScene(Scene scene, double betScore) {
        int[][] rotary = scene.getRotary();
        for (int i = 0; i < LotteryConfig.PRIZE_LINE.length; i++) {
            int[] prizeLine = LotteryConfig.PRIZE_LINE[i];
            int[] lineIcons = checkLineIcon(prizeLine, rotary);
            PrizeIcon prizeIconVo = checkPrize(lineIcons, prizeLine, i);
            if (prizeIconVo == null) continue;

            scene.getPrizeDetail().add(prizeIconVo);
            int mul = LotteryConfig.getMul(prizeIconVo.getIcon(), prizeIconVo.getLine());
            prizeIconVo.setMul(mul);
            double gold = betScore * mul / LotteryConfig.BASE_LINE;
            prizeIconVo.setGold(DecimalUtil.getBigDecimal2(gold));
            prizeIconVo.getPrizeIndex().forEach(l -> scene.getPrizeIndex().add(l));
            scene.setGold(DecimalUtil.getBigDecimal2(scene.getGold() + gold).doubleValue());
        }
    }

    @Override
    public int getCoolTime() {
        return 50;
    }

    @Override
    public double getWinGold() {
        return totalWinGold;
    }

    @Override
    public JSONObject codeResultData(Player gamePlayer, double betScore, double factor) {
        List<Scene> sceneIconVos = getResultScene(betScore, factor, gamePlayer);
        double totalWin = 0;
        int number = 0;
        for (Scene sceneIconVo : sceneIconVos) {
            totalWin += sceneIconVo.getGold();
            sceneIconVo.setNumber(number++);
            castRow2Columns(sceneIconVo);
        }

        this.totalWinGold = DecimalUtil.getBigDecimal2(totalWin).doubleValue();
        gamePlayer.getExtendJson().put(SCENE, sceneIconVos);
        gamePlayer.getExtendJson().put(BET_SCORE, betScore);
        return new JSONObject();
    }

    private void castRow2Columns(Scene sceneIconVo) {
        int[][] rotary = sceneIconVo.getRotary();
        int[][] castRotary = castArrR2C(rotary);
        sceneIconVo.setRotary(castRotary);

        BonusData bonusData = sceneIconVo.getBonusData();
        if (bonusData != null) {
            int[][] coinsScreen = bonusData.getCoins_screen();
            int[][] castCoinsScreen = castArrR2C(coinsScreen);
            bonusData.setCoins_screen(castCoinsScreen);
        }
    }

    private static int[][] castArrR2C(int[][] rotary) {
        int[][] castRotary = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {

                castRotary[i1][i] = rotary[i][i1];
            }
        }
        return castRotary;
    }

    @Override
    public double getCapacity(Player player, double betScore) {
        return 0;
    }

    @Override
    public JSONObject codeLogData(Player gamePlayer, GameInfo roomInfo) {
        JSONObject jsonObject = new JSONObject(true);
        List<Scene> fruitData = getScenes(gamePlayer);
        if (null != fruitData) {
            JSONArray jsonArray = new JSONArray();
            for (Scene fruitDatum : fruitData) {
                JSONObject object = (JSONObject) JSON.toJSON(fruitDatum);
                jsonArray.add(object);
            }
            jsonObject.put("iconData", jsonArray.toJSONString());
            jsonObject.put("uuid", TimeUtil.getNow());
            jsonObject.put("betMul", gamePlayer.getExtendJson().getInteger("betMul"));
            jsonObject.put(PARENT_ORDER, fruitData.get(0).getPOrder());
            return jsonObject;
        }
        log.error("{}.写入注单详情异常,场景为null", gamePlayer.getUser().getUserID());
        throw new RuntimeException("注单详情异常!");
    }

    @Override
    public void changeUi(Player gamePlayer, String s) {
    }

    /**
     * 获取注单详情
     *
     * @param data   客户端参数
     * @param player 待获取的玩家
     */
    @Override
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
        Object historyToClient = parseOrderDetailLog(jsonObject);
        log.info("userid = {},orderDetail => {}", player.getUser().getUserID(), JSONObject.toJSONString(historyToClient));
        return historyToClient;
    }

    private Object parseOrderDetailLog(JSONObject jsonObject) {
        RoundDetailDto historyToClient = new RoundDetailDto();
        try {
            int code = jsonObject.getInteger("code");
            if (code == 200) {
                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject jDetails = data.getJSONObject("details");
                JSONObject extData = jDetails.getJSONObject("extData");
                String gameDetail = extData.getString("iconData");
                return JSONArray.parseArray(gameDetail, RoundDetailDto.class);
            }
        } catch (Exception e) {
            log.error("rep record error", e);
        }
        return historyToClient;
    }
}
