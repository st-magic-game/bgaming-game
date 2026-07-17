package com.bgaming.diamondofjungle.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.diamondofjungle.config.LotteryConfig;
import com.bgaming.diamondofjungle.entity.PrizeIcon;
import com.bgaming.diamondofjungle.entity.Scene;
import com.bgaming.diamondofjungle.entity.dto.GameFeatures;
import com.bgaming.diamondofjungle.entity.dto.OutCome;
import com.bgaming.diamondofjungle.entity.dto.RoundDetailDto;
import com.bgaming.diamondofjungle.entity.dto.SpinResponse;
import com.bgaming.diamondofjungle.utils.DateTimeUtil;
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

import static com.bgaming.diamondofjungle.config.LotteryConfig.BET_TYPE;
import static com.bgaming.diamondofjungle.config.LotteryConfig.FREE_NUM;
import static com.bgaming.diamondofjungle.config.LotteryConfig.*;
import static com.game.base.common.constant.GameKey.*;
import static com.game.base.common.constant.Protocol.*;

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
            double beforeScore = player.getUser().getScore();
            JSONObject jData = JSONObject.parseObject(data);
            JSONObject options = jData.getJSONObject(OPTIONS);
            String command = jData.getString("command");
            int userId = player.getUserId();
            Double stake;
            if (environmentCheck(player, userId)) return null;

            int requestType = 0; // 0 普通  1 free  2 super free
            List<Scene> scenes = getScenes(player);
            int times = player.getETimes();
            if (command.equals("freespin")) {
                if (scenes == null || scenes.isEmpty() || times + 1 >= scenes.size()) {
                    log.error("userId {} , error request freeSpin1, scene == null", userId);
                    return null;
                }

                if (isErrorRequest(scenes, times)) {
                    log.error("userId {} , error request freeSpin2, scene == null", userId);
                    return null;
                }
                stake = scenes.get(0).getBetScoreServer() * LotteryConfig.SUB_UNITS;
                times++;
                SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue(), beforeScore, times);
                player.setETimes(times);
                log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                return response;
            } else {
                if (isErrorSpinReq(scenes, times)) {
                    log.error("userId {} , error request spin, scenes size {} , times {}", userId, scenes.size(), times);
                    return null;
                }
                stake = DecimalUtil.getBigDecimal2(options.getDouble(BET)).doubleValue();
                if (options.containsKey(LotteryConfig.PURCHASED_FEATURE)) {
                    String purchasedFeature = options.getString(LotteryConfig.PURCHASED_FEATURE);
                    if (purchasedFeature.equals(PURCHASED_FREESPIN_BUY)) {
                        String level = options.getString("purchased_feature_level");
                        requestType = Integer.parseInt(level);
                    }
                }
            }

            String volatility = options.getString("volatility");
            if (cheatingDetection(player, stake, volatility)) return null;

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}", player.getUser().getUserID(), stake);
                return null;
            }

            stake = DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue();
            double orderStake = DecimalUtil.getBigDecimal2(stake * BET_TYPE_MUL[requestType]).doubleValue();
            if (notEnoughGold(orderStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, stake, orderStake);
                return null;
            }

            this.lastStartTime = TimeUtil.getNow();
            player.getExtendJson().put(BET_MUL, 1);
            player.getExtendJson().put(BET_TYPE, requestType);
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
            String pOrder = player.getUser().getUserID() + "-" + TimeUtil.getNow();
            Scene scene = scenes.get(0);
            scene.setAfterScore(DecimalUtil.getBigDecimal2(beforeScore - orderStake).doubleValue());
            scene.setFreeType(requestType);
            scene.setVolatility(volatility);
            scene.setPOrder(pOrder);
            player.getExtendJson().put("pOrder", pOrder);
            player.initBetId(gameInfo.getRoomID(), scene.getOrder());
            player.setBetIdNum(0);
            SpinResponse response = getSpinResponse(player, orderStake, scenes, stake, beforeScore, 0);
            log.info("玩家 {}  数据 result {}", player.getUserId(), response);
            return response;
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }

    private SpinResponse getSpinResponse(Player player, double orderStake, List<Scene> scenes, Double stake, double beforeScore, int times) {
        Scene scene = scenes.get(times);
        boolean finish = checkFinishScene(scene);
        if (orderStake > 0) {
            double winGold = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
            boolean isLastOrder = finish && winGold == 0;
            JSONObject extendData = getExtendData(player, scenes.get(0), isLastOrder);
            setCurData(player, orderStake, 0);
            this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo,
                    orderStake, 0, 0, scenes.get(0).getPOrder(), extendData, isLastOrder ? 1 : 0, false);
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
        SpinResponse response = generateResponse(scenes.subList(0, times + 1), finish, DecimalUtil.getBigDecimal2(player.getUser().getScore()).doubleValue());
        List<RoundDetailDto> roundDetailDtos = new ArrayList<>();
        if (finish) {
            for (Scene tmpScene : scenes) {
                RoundDetailDto roundDetailDto = generateRoundDetail(tmpScene.getBeforeScore(), player, tmpScene);
                roundDetailDtos.add(roundDetailDto);
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

    private static boolean checkFinishScene(Scene scene) {
        return (scene.getType() == 0 && scene.getOpenFreeNum() == 0)
                || (scene.getType() == 1 && scene.getFreeNum() == 1 && scene.getOpenFreeNum() == 0);
    }

    private JSONObject getExtendData(Player player, Scene scene, boolean finish) {
        JSONObject extendData = getExtendString(player, scene.getPOrder(), finish);
        extendData.put(FREE_TYPE, scene.getFreeType());
        extendData.put(BUY_TYPE, 0);
        extendData.put(BET_TYPE, 0);
        return extendData;
    }

    private boolean isErrorSpinReq(List<Scene> scenes, int eTimes) {
        if (scenes == null || scenes.isEmpty()) return false;

        if (scenes.size() > 1 && eTimes < scenes.size() - 1) return true;
        return false;
    }

    private static boolean isErrorRequest(List<Scene> scenes, int times) {
        boolean errorRequest = false;
        Scene lastScene = scenes.get(times);
        if (lastScene.getType() == 0) {
            if (lastScene.getOpenFreeNum() == 0) {
                errorRequest = true;
            }
        } else {
            if (lastScene.getFreeNum() == 1 && lastScene.getOpenFreeNum() == 0) {
                errorRequest = true;
            }
        }
        return errorRequest;
    }

    private RoundDetailDto generateRoundDetail(double beforeScore, Player player, Scene scene) {
        int[][] rotary = scene.getRotary();
        List<PrizeIcon> prizeDetail = scene.getPrizeDetail();
        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));
        roundDetailDto.setUsedFeature(false);
        BigDecimal realBet = DecimalUtil.getBigDecimal2(scene.getBetScore());
        BigDecimal realWin = DecimalUtil.getBigDecimal2(scene.getGold());
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(scene.getGold() - scene.getBetScore());
        roundDetailDto.setBetText(realBet.stripTrailingZeros().toPlainString());
        roundDetailDto.setBet(realBet);
        roundDetailDto.setType(scene.getType());
        roundDetailDto.setTotalWinText(realWin.stripTrailingZeros().toPlainString());
        roundDetailDto.setTotalWin(realWin);
        roundDetailDto.setBaseBetText(DecimalUtil.getBigDecimal2(scene.getBetScoreServer()).stripTrailingZeros().toPlainString());
        roundDetailDto.setProfitText(realProfit.stripTrailingZeros().toPlainString());
        roundDetailDto.setProfit(realProfit);
        roundDetailDto.setCurrency(player.getCoinsType());
        roundDetailDto.setBalanceBeforeText(DecimalUtil.getBigDecimal2(beforeScore).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceBefore(DecimalUtil.getBigDecimal2(beforeScore));
        roundDetailDto.setBalanceAfterText(DecimalUtil.getBigDecimal2(player.getUser().getScore()).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceAfter(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
        roundDetailDto.setWinLines(castDetailWinLine(prizeDetail, scene.getDoubleMul()));
        roundDetailDto.setFeatureName(scene.getFreeType() > 0 ? "Freespin buy" : "No");
        roundDetailDto.setVolatility(scene.getVolatility());
        roundDetailDto.setDoubleMul(scene.getDoubleMul());
        if (scene.getScatterWin() != null && scene.getScatterWin().doubleValue() > 0) {
            roundDetailDto.setScatterWin(scene.getScatterWin());
            roundDetailDto.setScatterSize(scene.getScatterSize() + "x ");
            roundDetailDto.setOpenFreeNum(scene.getOpenFreeNum());
            roundDetailDto.setScatterWinText("= " + scene.getScatterWin().stripTrailingZeros().toPlainString());
        }
        roundDetailDto.setSymbols(castDetailSymbol(rotary));
        return roundDetailDto;
    }

    private List<List<String>> castDetailWinLine(List<PrizeIcon> prizeDetail, int doubleMul) {
        List<List<String>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == SCATTER) continue;

            String line = String.valueOf(prizeIcon.getLine()).concat("x");
            String iconStr = SYMBOL_NAME[prizeIcon.getIcon()];
            String lineIdEndPos;
            if (doubleMul > 1) {
                String formatStr = "= %s * Multiplier(x%s) = %s";
                String totalWinStr = prizeIcon.getGold().stripTrailingZeros().toPlainString();
                String baseWin = DecimalUtil.getBigDecimal2(prizeIcon.getGold().doubleValue() / doubleMul).stripTrailingZeros().toPlainString();
                lineIdEndPos = String.format(formatStr, baseWin, doubleMul, totalWinStr);
            } else {
                lineIdEndPos = "= " + prizeIcon.getGold().stripTrailingZeros().toPlainString();
            }
            List<String> winLine = Arrays.asList(line, iconStr, lineIdEndPos);
            result.add(winLine);
        }
        return result;
    }

    private List<String> castDetailSymbol(int[][] rotary) {
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int icon = rotary[i][i1];
                symbols.add(SYMBOL_NAME[icon]);
            }
        }
        return symbols;
    }

    private static final String[] SYMBOL_NAME = {"", "h1", "h2", "m1", "m2", "l1", "l2", "l3", "l4", "scatter"};

    private void resetPlayerExt(Player player) {
        player.setETimes(0);
        player.getExtendJson().remove(GameKey.BET_TYPE);
        player.getExtendJson().remove(BUY_FREE);
    }

    public SpinResponse generateResponse(List<Scene> scenes, boolean finish, double betAfterScore) {
        String volatility = scenes.get(0).getVolatility();
        Scene scene = scenes.get(scenes.size() - 1);
        Double totalWin = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
        double betScore = scene.getBetScoreServer();
        String orderId = scene.getOrder();
        int orderSer = scene.getNumber() + 1;
        BgBalance balance = new BgBalance();
        balance.setGame(DecimalUtil.getBigDecimal2(totalWin * SUB_UNITS));
        balance.setWallet(DecimalUtil.getBigDecimal2(betAfterScore * SUB_UNITS));

        FlowData flowData = this.table.getGameService().initFlowData();
        List<String> actions = new ArrayList<>();
        actions.add(BGAMING_COMMAND_INIT);
        if (scene.getOpenFreeNum() > 0) {
            flowData.setState(BGAMING_COMMAND_FREE_SPIN);
            actions.add("freespin");
        } else {
            if (scene.getType() == 1) {
                actions.add("freespin");
                flowData.setState(finish ? BGAMING_STATE_CLOSED : BGAMING_COMMAND_FREE_SPIN);
            } else {
                actions.add(BGAMING_COMMAND_SPIN);
                flowData.setState(BGAMING_STATE_CLOSED);
            }
        }
        String command = scene.getType() == 0 ? BGAMING_COMMAND_SPIN : "freespin";
        flowData.setAvailable_actions(actions);
        flowData.setCommand(command);
        flowData.setRound_id(orderId);
        flowData.setLast_action_id(orderId + "_" + orderSer);

        String orderNum = orderId.replaceAll("-", "");
        String orderN = orderNum.substring(orderNum.length() - 11);
        long orderLong = Long.parseLong(orderN);
        JSONObject jsonObject = JSONObject.parseObject(JSONObject.toJSONString(flowData));
        jsonObject.put("round_id", orderLong);
        jsonObject.put("last_action_id", orderLong + "_" + orderSer);

        Optional<Double> freeTotalWinOptional = scenes.stream().filter(s -> s.getType() == 1).map(Scene::getGold).reduce(Double::sum);
        double freeTotalWin = 0;
        if (freeTotalWinOptional.isPresent()) {
            freeTotalWin = freeTotalWinOptional.get();
        }
        int freeSpinIssued = checkIssued(scene);
        int freeSpinLeft = checkFreeSpinLeft(scene);
        GameFeatures gameFeatures = new GameFeatures();
        gameFeatures.setFreespins_issued(freeSpinIssued);
        gameFeatures.setFreespins_left(freeSpinLeft);
        gameFeatures.setVolatility(volatility);
        gameFeatures.setTotal_fs_win(DecimalUtil.getBigDecimal2(freeTotalWin));

        JSONObject storage = new JSONObject();
        storage.put("wilds", new int[5][0]);
        if (scene.getDoubleMul() > 1) {
            storage.put("fs_multiplier", scene.getDoubleMul());
        }
        OutCome outCome = new OutCome();
        outCome.setBet(DecimalUtil.getBigDecimal2(betScore * SUB_UNITS));
        outCome.setWin(DecimalUtil.getBigDecimal2(scene.getGold() * SUB_UNITS));
        outCome.setSpecial_symbols(checkSpecialSymbol(scene.getRotary()));
        outCome.setWins(checkWins(scene.getPrizeDetail()));
        outCome.setScreen(castReel(scene.getRotary()));
        outCome.setFreespins_issued(scene.getOpenFreeNum() == 0 ? null : scene.getOpenFreeNum());
        outCome.setStorage(storage);

        SpinResponse gameResponse = new SpinResponse();
        gameResponse.setApi_version(this.table.getGameService().getBaseVersion().getApiVersion());
        gameResponse.setBalance(balance);
        gameResponse.setFlow(jsonObject);
        gameResponse.setOutcome(outCome);
        gameResponse.setFeatures(gameFeatures);
        return gameResponse;
    }

    private int checkFreeSpinLeft(Scene scene) {
        if (scene.getType() == 1) {
            if (scene.getOpenFreeNum() == 0) {
                return scene.getFreeNum() - 1;
            } else {
                return scene.getFreeNum() + scene.getOpenFreeNum() - 1;
            }
        }
        if (scene.getOpenFreeNum() > 0) {
            return scene.getOpenFreeNum();
        }
        return 0;
    }

    private int checkIssued(Scene scene) {
        return scene.getType() == 0 ? scene.getOpenFreeNum() : scene.getTotalFreeNum();
    }

    private List<List<String>> castReel(int[][] rotary) {
        List<List<String>> screen = new ArrayList<>();
        for (int i = 0; i < COLUMNS; i++) {
            int icon1 = rotary[0][i];
            int icon2 = rotary[1][i];
            int icon3 = rotary[2][i];
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
            if (prizeIcon.getIcon() == SCATTER) {
                Set<Integer> prizeIndex = prizeIcon.getPrizeIndex();
                List<List<Integer>> scatterIndex = new ArrayList<>();
                for (Integer index : prizeIndex) {
                    List<Integer> idx = new ArrayList<>();
                    idx.add(index % COLUMNS);
                    idx.add(index / COLUMNS);
                    scatterIndex.add(idx);
                }
                win.add("scatter");
                win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(SUB_UNITS)));
                win.add(scatterIndex);
            } else {
                win.add("ways");
                win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(SUB_UNITS)));
                win.add(castColumnIdx(prizeIcon.getPrizeIndex(), prizeIcon.getLine()));
            }
            result.add(win);
        }
        return result;
    }

    private List<List<Integer>> castColumnIdx(Set<Integer> prizeIndex, int line) {
        List<List<Integer>> index = initWinsBase(line);
        for (Integer idx : prizeIndex) {
            index.get(idx % COLUMNS).add(idx / COLUMNS);
        }
        return index;
    }

    private List<List<Integer>> initWinsBase(int line) {
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < line; i++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private JSONObject checkSpecialSymbol(int[][] rotary) {
        JSONObject result = new JSONObject();
        List<List<Integer>> indexes = new ArrayList<>();
        List<List<Integer>> scatterIndexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int icon = rotary[i][i1];
                if (icon == SCATTER) {
                    List<Integer> index = new ArrayList<>();
                    index.add(i1);
                    index.add(i);
                    scatterIndexes.add(index);
                }
            }
        }
        if (!scatterIndexes.isEmpty()) {
            Map<Integer, List<List<Integer>>> inner = new HashMap<>();
            inner.put(SCATTER, scatterIndexes);
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

    private static boolean cheatingDetection(Player player, Double stake, String volatility) {
        if (stake < 0) {
            log.error("user {} , 作弊检测篡改数据!!! betScore {}", player.getUser().getUserID(), stake);
            return true;
        }

        if (!volatility.trim().equals("medium") && !volatility.trim().equals("low")) {
            log.error("user {} , 作弊检测篡改数据!!! volatility {}", player.getUser().getUserID(), volatility);
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
     * @param finish
     * @return 扩资数据
     */
    private JSONObject getExtendString(Player player, String pOrder, boolean finish) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送注单时，服务器发生错误");
            throw new RuntimeException("发送注单数据错误");
        }

        JSONObject prizeStatistics = this.getPrizeStatistics(scenes, pOrder);
        prizeStatistics.put("lastOrder", finish);

        return prizeStatistics;
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

    private int getBetType(Player player) {
        int betType = 0;
        if (player.getExtendJson().containsKey(BET_TYPE)) {
            betType = player.getExtendJson().getInteger(BET_TYPE);
        }
        return betType;
    }

    private List<Scene> getResultScene(Player player, double betScore, double factor) {
        int betType = getBetType(player);
        int freeType = betType;
        List<Scene> result = new ArrayList<>();
        long now = TimeUtil.getNow();
        int type = 0;
        int freeNum = 0;
        int number = 0;
        int totalFreeNum = 0;
        double tmpFac = factor;
        do {
            if (type == 1) {
                tmpFac = factor * BET_TYPE_PRO[freeType];
            }
            Scene sceneIconVo = generatedScene(betType, freeType, betScore, tmpFac, type);
            checkAndSetFreeInfo(sceneIconVo, betScore);
            totalFreeNum += sceneIconVo.getOpenFreeNum();
            sceneIconVo.setOrder(nextId(now));
            sceneIconVo.setFreeNum(freeNum);
            sceneIconVo.setNumber(number++);
            sceneIconVo.setTotalFreeNum(totalFreeNum);
            if (sceneIconVo.getOpenFreeNum() > 0) {
                freeNum += sceneIconVo.getOpenFreeNum();
                type = 1;
            }
            if (sceneIconVo.getType() == 1) {
                freeNum--;
            }
            if (sceneIconVo.getGold() == 0) {
                sceneIconVo.setDoubleMul(1);
            }
            result.add(sceneIconVo);
            betType = 0;
        } while (freeNum > 0);

        return result;
    }

    private void checkAndSetFreeInfo(Scene sceneIconVo, double betScore) {
        int[][] rotary = sceneIconVo.getRotary();
        List<Integer> scatterIndexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == SCATTER) {
                    scatterIndexes.add(i * COLUMNS + i1);
                }
            }
        }

        if (scatterIndexes.size() >= 3) {
            PrizeIcon prizeIcon = new PrizeIcon();
            int openFreeNum = FREE_NUM[scatterIndexes.size() - 3];
            int mul = getMul(SCATTER, scatterIndexes.size());
            double scatterWinGold = betScore * mul * sceneIconVo.getDoubleMul();
            prizeIcon.setIcon(SCATTER);
            prizeIcon.setMul(mul);
            prizeIcon.setGold(DecimalUtil.getBigDecimal2(scatterWinGold));
            prizeIcon.setNum(-1);
            prizeIcon.setPrizeIndex(new HashSet<>(scatterIndexes));
            prizeIcon.setLine(openFreeNum);
            sceneIconVo.getPrizeDetail().add(prizeIcon);
            sceneIconVo.setGold(DecimalUtil.getBigDecimal2(sceneIconVo.getGold() + scatterWinGold).doubleValue());
            sceneIconVo.getPrizeIndex().addAll(scatterIndexes);
            sceneIconVo.setOpenFreeNum(openFreeNum);
            sceneIconVo.setScatterSize(scatterIndexes.size());
            sceneIconVo.setScatterWin(DecimalUtil.getBigDecimal2(scatterWinGold));
        }
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
                for (int i1 = 0; i1 < COLUMNS; i1++) {
                    list.add(RandomUtil.nextInt(ROWS) * COLUMNS + i1);
                }
                prizeLines.add(list);
            }

            for (List<Integer> prizeLine : prizeLines) {
                //随机找一个图标
                int icon = LotteryConfig.ICONS_WITH_MULTIPLE[RandomUtil.nextInt(2, ICONS_WITH_MULTIPLE.length)];
                for (int i1 = 0; i1 < prizeLine.size(); i1++) {
                    if (i1 == 2) continue;

                    if (i1 == 4 && RandomUtil.nextDouble() <= 0.4) continue;
                    int index = prizeLine.get(i1);
                    rotary[index / COLUMNS][index % COLUMNS] = icon;
                }
            }
        }
    }

    /**
     * 生成场景
     */
    private static Scene generatedScene(int betType, int freeType, double betScore, double factor, int type) {
        Scene scene = new Scene();
        scene.setType(type);
        if (type == 1) {
            int doubleMul = getDoubleMul(freeType, factor);
            scene.setDoubleMul(doubleMul);
        }
        int[][] rotary = getInitRotary();
        sceneLoneLines(rotary, type == 1 ? factor * 0.1 : factor);
        int scatterSize;
        if (betType > 0) {
            scatterSize = 3;
            if (RandomUtil.nextDouble() < 0.01) {
                scatterSize += 1;
            }
        } else {
            scatterSize = getScatterSize(type == 1 ? 0.5 * factor : factor);
        }
        installScatter(rotary, scatterSize);
        //随机填充1，2，4，5个转轴的图标
        for (int i = 0; i < COLUMNS; i++) {
            if (i == 2) continue;

            for (int i1 = 0; i1 < ROWS; i1++) {
                if (rotary[i1][i] == -1) {
                    rotary[i1][i] = LotteryConfig.getRandomNormalIcon();
                }
            }
        }
        List<PrizeIcon> list = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            int icon = rotary[i][0];
            if (icon == SCATTER) continue;

            PrizeIcon prizeIcon = getPrizeIcon(rotary, icon);
            if (prizeIcon != null) {
                list.add(prizeIcon);
            }
        }
        double fixedValue = 1.0d;
        int repeatCount = 0;
        factor = Math.pow(factor, 2);
        if (!list.isEmpty() && betType == 0) {
            for (int i = 0; i < ROWS; i++) {
                if (rotary[i][2] == -1) {
                    PrizeIcon prizeIcon = list.get(RandomUtil.nextInt(list.size()));
                    double mul = scene.getDoubleMul() * getMul(prizeIcon.getIcon(), prizeIcon.getLine()) * 1.0d / BASE_LINE;
                    if (mul < 1) {
                        factor *= 0.205;
                    }

                    if (RandomUtil.nextDouble() < factor * SMALL_WIN_PRO * fixedValue / mul) {
                        if (repeatCount > 1 && RandomUtil.nextDouble() < 0.75) continue;

                        rotary[i][2] = prizeIcon.getIcon();
                        if (list.size() == 1 && RandomUtil.nextDouble() < 0.333) {
                            repeatCount++;
                        }
                    }
                }
            }
        }
        List<Integer> iconNumWithColumn = getIconNumWithColumn(rotary, -1, 2, 0);
        if (!iconNumWithColumn.isEmpty()) {
            List<Integer> usePoint = new ArrayList<>();
            for (int j : ICONS_WITH_MULTIPLE) {
                if (list.stream().noneMatch(l -> l.getIcon() == j)) {
                    usePoint.add(j);
                }
            }
            for (int i = 0; i < ROWS; i++) {
                if (rotary[i][2] == -1) {
                    rotary[i][2] = usePoint.get(RandomUtil.nextInt(usePoint.size()));
                }
            }
        }
        scene.setRotary(rotary);
        setMulWithScene(scene, betScore);
        return scene;
    }


    /**
     * @param scene 场景
     * @param icon  图标
     * @return 获取可能中奖的图标
     */
    private static PrizeIcon getPrizeIcon(int[][] scene, int icon) {
        int num = 1;
        List<Integer> tempIndex;
        //第1个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 0, 0);
        num *= tempIndex.size();
        Set<Integer> iconIndex = new HashSet<>(tempIndex);
        //第2个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 1, 0);
        if (tempIndex.isEmpty()) return null;

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        //第4个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 3, 0);
        if (tempIndex.isEmpty()) return new PrizeIcon(icon, 3, num, iconIndex);

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        //第5个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 4, 0);
        if (tempIndex.isEmpty()) return new PrizeIcon(icon, 4, num, iconIndex);

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        return new PrizeIcon(icon, 5, num, iconIndex);
    }

    private static void installScatter(int[][] rotary, int scatterSize) {
        if (scatterSize == 0) return;

        List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        for (int i = 0; i < scatterSize; i++) {
            Integer index = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
            rotary[RandomUtil.nextInt(ROWS)][index] = SCATTER;
        }
    }

    /**
     * @return 获取初始化转轴列表
     */
    private static int[][] getInitRotary() {
        int[][] rotary = new int[ROWS][COLUMNS];
        for (int[] ints : rotary) {
            Arrays.fill(ints, -1);
        }
        return rotary;
    }

    private static void setMulWithScene(Scene scene, double betScore) {
        for (int i = 0; i < ROWS; i++) {
            int icon = scene.getRotary()[i][0];
            if (icon == SCATTER) continue;

            PrizeIcon prizeIcon = getFinalPrizeIcon(scene.getRotary(), icon, i * COLUMNS);
            if (prizeIcon != null) {
                int mul = getMul(icon, prizeIcon.getLine()) * prizeIcon.getNum() * scene.getDoubleMul();
                scene.getPrizeIndex().addAll(prizeIcon.getPrizeIndex());
                prizeIcon.setMul(mul);
                if (scene.getPrizeDetail().stream().anyMatch(p -> p.getIcon() == prizeIcon.getIcon())) {
                    for (PrizeIcon p : scene.getPrizeDetail()) {
                        if (p.getIcon() == prizeIcon.getIcon()) {
                            p.setMul(p.getMul() + mul);
                            p.setNum(p.getNum() + prizeIcon.getNum());
                            p.getPrizeIndex().addAll(prizeIcon.getPrizeIndex());
                            break;
                        }
                    }
                } else {
                    scene.getPrizeDetail().add(prizeIcon);
                }
            }
        }

        double sceneWinGold = 0;
        for (PrizeIcon prizeIcon : scene.getPrizeDetail()) {
            prizeIcon.setGold(DecimalUtil.getBigDecimal2(prizeIcon.getMul() * betScore / BASE_LINE));
            sceneWinGold += prizeIcon.getGold().doubleValue();
        }
        scene.setGold(DecimalUtil.getBigDecimal2(sceneWinGold).doubleValue());
    }

    /**
     * @param scene 场景
     * @param icon  图标
     * @return 获取可能中奖的图标
     */
    private static PrizeIcon getFinalPrizeIcon(int[][] scene, int icon, int index) {
        int num = 1;
        List<Integer> tempIndex;
        //第1个转轴
        Set<Integer> iconIndex = new HashSet<>();
        iconIndex.add(index);
        //第2个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 1, 0);
        if (tempIndex.isEmpty()) return null;

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        //第3个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 2, 0);
        if (tempIndex.isEmpty()) return null;

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        //第4个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 3, 0);
        if (tempIndex.isEmpty()) return new PrizeIcon(icon, 3, num, iconIndex);

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        //第5个转轴
        tempIndex = getIconNumWithColumn(scene, icon, 4, 0);
        if (tempIndex.isEmpty()) return new PrizeIcon(icon, 4, num, iconIndex);

        num *= tempIndex.size();
        iconIndex.addAll(tempIndex);
        return new PrizeIcon(icon, 5, num, iconIndex);
    }

    private static List<Integer> getIconNumWithColumn(int[][] scene, int icon, int column, int startRows) {
        List<Integer> iconIndex = new ArrayList<>();
        for (int i = startRows; i < ROWS; i++) {
            if (scene[i][column] == icon) {
                iconIndex.add(i * COLUMNS + column);
            }
        }
        return iconIndex;
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
        List<Scene> sceneIconVos = getResultScene(gamePlayer, betScore, factor);
        double totalWin = sceneIconVos.stream().map(Scene::getGold).reduce(Double::sum).get();
        this.totalWinGold = DecimalUtil.getBigDecimal2(totalWin).doubleValue();
        gamePlayer.getExtendJson().put(SCENE, sceneIconVos);
        gamePlayer.getExtendJson().put(BET_SCORE, betScore);
        return new JSONObject();
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
