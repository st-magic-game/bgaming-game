package com.bgaming.catdiana.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.catdiana.config.LotteryConfig;
import com.bgaming.catdiana.config.ReelConfig;
import com.bgaming.catdiana.entity.PrizeIcon;
import com.bgaming.catdiana.entity.Scene;
import com.bgaming.catdiana.entity.dto.*;
import com.bgaming.catdiana.utils.DateTimeUtil;
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
import com.game.base.interfaces.dto.bgaming.FlowData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

import static com.bgaming.catdiana.config.LotteryConfig.*;
import static com.bgaming.catdiana.config.ReelConfig.ICON_NAME;
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
            JSONObject lineBetMap = options.getJSONObject("bets");
            int userId = player.getUserId();
            if (environmentCheck(player, userId)) return null;

            List<Scene> scenes = getScenes(player);
            int times = player.getETimes();
            if (lineBetMap.containsKey("respin")) {
                if (lineBetMap.getBoolean("respin")) {
                    if (isErrorRespinRequest(scenes, times)) {
                        log.error("userId {} , error request respin, scene == null", userId);
                        return null;
                    }

                    times++;
                    SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(scenes.get(0).getBetScore()).doubleValue(), beforeScore, times);
                    player.setETimes(times);
                    log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                    return response;
                }
            }

            Integer lineBet = lineBetMap.getInteger("0");
            double stake = DecimalUtil.getBigDecimal2(lineBet * BASE_LINE).doubleValue();
            if (cheatingDetection(player, stake)) return null;

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}", player.getUser().getUserID(), stake);
                return null;
            }


            if (scenes != null && !scenes.isEmpty() && times < scenes.size() - 1) {
                if (isErrorRequest(scenes, stake, times)) {
                    log.error("userId {} , error request freeSpin2, scene == null", userId);
                    return null;
                }
                times++;
                SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue(), beforeScore, times);
                player.setETimes(times);
                log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                return response;
            } else {
                if (isErrorSpinReq(scenes, times)) {
                    log.error("userId {} , error request spin, scenes {}", userId, JSONObject.toJSONString(scenes));
                    return null;
                }
            }
            double orderStake = stake;
            if (notEnoughGold(orderStake / SUB_UNITS, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, stake, orderStake);
                return null;
            }
            stake = DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue();
            orderStake = stake;
            this.lastStartTime = TimeUtil.getNow();
            player.getExtendJson().put(BET_MUL, 1);
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
            scene.setBetLine(lineBetMap);
            scene.setAfterScore(DecimalUtil.getBigDecimal2(beforeScore - orderStake).doubleValue());
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

    private boolean isErrorRespinRequest(List<Scene> scenes, int times) {
        if (scenes == null || scenes.size() <= 1 || times >= scenes.size()) {
            return true;
        }
        boolean errorRequest = false;
        Scene lastScene = scenes.get(times);
        if (lastScene.getType() == 0) {
            if (lastScene.getRespinCount() == 0) {
                errorRequest = true;
            }
        } else {
            if (lastScene.getRespinCount() == 0) {
                errorRequest = true;
            }
        }
        return errorRequest;
    }

    public SpinResponse getSpinResponse(Player player, double orderStake, List<Scene> scenes, Double stake, double beforeScore, int times) {
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
        SpinResponse response = generateResponse(scenes.subList(0, times + 1), finish, DecimalUtil.getBigDecimal2(player.getUser().getScore()).doubleValue());
        player.getExtendJson().put("spinResponse", response);
        if (finish) {
            player.getExtendJson().remove(SCENE);
            player.getExtendJson().remove("spinResponse");
        }
        return response;
    }

    private static boolean checkFinishScene(Scene scene) {
        return (scene.getType() == 0 && scene.getOpenFreeNum() == 0 && scene.getRespinCount() == 0)
                || (scene.getType() == 1 && scene.getFreeNum() == 1 && scene.getOpenFreeNum() == 0)
                || (scene.getType() == 2 && scene.getRespinCount() == 0);
    }

    private JSONObject getExtendData(Player player, Scene scene, boolean finish) {
        JSONObject extendData = getExtendString(player, scene.getPOrder(), finish);
        extendData.put(FREE_TYPE, scene.getFreeType());
        extendData.put(BUY_TYPE, 0);
        extendData.put(BET_TYPE, 0);
        return extendData;
    }

    private boolean isErrorSpinReq(List<Scene> scenes, int times) {
        if (scenes == null || scenes.isEmpty()) return false;

        Scene lastScene = scenes.get(times);
        if (lastScene.getOpenFreeNum() > 0) return true;

        if (lastScene.getType() == 1) {
            if (lastScene.getFreeNum() > 1) {
                return true;
            }
        }

        if (lastScene.getType() == 2) {
            if (lastScene.getRespinCount() > 0) {
                return true;
            }
        }

        return false;
    }

    private static boolean isErrorRequest(List<Scene> scenes, Double stake, int times) {
        boolean errorRequest = false;
        Scene lastScene = scenes.get(times);
        if (lastScene.getBetScoreServer() != DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue()) {
            errorRequest = true;
        }
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
        List<PrizeIcon> prizeDetail = scene.getPrizeDetail();
        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));
        roundDetailDto.setUsedFeature(false);
        BigDecimal realBet = DecimalUtil.getBigDecimal2(scene.getBetScoreServer());
        BigDecimal realWin = DecimalUtil.getBigDecimal2(scene.getGold());
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(scene.getGold() - scene.getBetScore());
        roundDetailDto.setBetText(realBet.stripTrailingZeros().toPlainString());
        roundDetailDto.setBet(realBet);
        roundDetailDto.setType(scene.getType());
        roundDetailDto.setTotalWinText(realWin.stripTrailingZeros().toPlainString());
        roundDetailDto.setTotalWin(realWin);
        roundDetailDto.setPerText(DecimalUtil.getBigDecimal2(realBet.doubleValue() / BASE_LINE).stripTrailingZeros().toPlainString());
        roundDetailDto.setProfitText(realProfit.stripTrailingZeros().toPlainString());
        roundDetailDto.setProfit(realProfit);
        roundDetailDto.setCurrency(player.getCoinsType());
        roundDetailDto.setBalanceBeforeText(DecimalUtil.getBigDecimal2(beforeScore).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceBefore(DecimalUtil.getBigDecimal2(beforeScore));
        roundDetailDto.setBalanceAfterText(DecimalUtil.getBigDecimal2(player.getUser().getScore()).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceAfter(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
        roundDetailDto.setWinLines(castDetailWinLine(prizeDetail));
        if (scene.getType() == 2) {
            int sumCoinsMul = sumCoinsMul(scene.getCoins());
            roundDetailDto.setCoinsWinText(DecimalUtil.getBigDecimal2(sumCoinsMul * scene.getBetScoreServer()).stripTrailingZeros().toPlainString());
        }
        if (scene.getScatterWin() != null && scene.getScatterWin().doubleValue() > 0) {
            roundDetailDto.setScatterWin(scene.getScatterWin());
            roundDetailDto.setScatterWinText(scene.getScatterWin().stripTrailingZeros().toPlainString());
        }
        roundDetailDto.setSymbols(castDetailSymbol(scene));
        return roundDetailDto;
    }

    private List<List<String>> castDetailWinLine(List<PrizeIcon> prizeDetail) {
        List<List<String>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == SCATTER) continue;

            String line = String.valueOf(prizeIcon.getLine()).concat("x");
            String iconStr = ICON_NAME.get(prizeIcon.getIcon());
            String lineIdEndPos = "Line ".concat(String.valueOf(prizeIcon.getHitLine() + 1)).concat(" - ").concat(prizeIcon.getGold().stripTrailingZeros().toPlainString());
            List<String> winLine = Arrays.asList(line, iconStr, lineIdEndPos);
            result.add(winLine);
        }
        return result;
    }

    private List<SymbolInfo> castDetailSymbol(Scene scene) {
        double bet = scene.getBetScoreServer();
        List<List<Integer>> coinsNew = scene.getCoins_new_total();
        int[][] rotary = scene.getRotary();
        int[][] coins = scene.getCoins();
        List<SymbolInfo> symbols = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int icon = rotary[i][i1];
                String symbolName = ICON_NAME.get(icon);
                SymbolInfo symbolInfo = new SymbolInfo();
                symbolInfo.setName(symbolName);
                if (scene.getType() == 2) {
                    symbolInfo.setStyle("opacity: 0; ;");
                } else {
                    symbolInfo.setStyle("opacity: 1; ;");
                }
                if (icon == COIN) {
                    symbolInfo.setStyle("opacity: 1; ;");
                    BigDecimal score = DecimalUtil.getBigDecimal2(coins[i1][i] * bet);
                    String coinScore;
                    if (score.stripTrailingZeros().scale() <= 0) {
                        coinScore = score.setScale(1).toPlainString();
                    } else {
                        coinScore = score.stripTrailingZeros().toPlainString();
                    }
                    symbolInfo.setScore(coinScore);

                    int marginLeft = marginLeftByLen(coinScore.length());
                    symbolInfo.setMarginLeft(marginLeft);
                    if (scene.getType() == 2 && isCoinsNew(coinsNew, i, i1)) {
                        symbolInfo.setStyle("opacity: 1; background-color: orange;;");
                    }
                }
                symbols.add(symbolInfo);
            }
        }
        return symbols;
    }

    private boolean isCoinsNew(List<List<Integer>> coinsNew, int y, int x) {
        for (List<Integer> integers : coinsNew) {
            if (integers.get(0) == x && integers.get(1) == y) {
                return true;
            }
        }
        return false;
    }

    //3-75 4-65 5-55 6-
    private static final int[] MARGIN_LEFT = {75, 65, 55, 45, 35};

    private int marginLeftByLen(int length) {
        if (length < 3) {
            length = 3;
        }
        if (length > 7) {
            length = 7;
        }
        return MARGIN_LEFT[length - 3];
    }

    private void resetPlayerExt(Player player) {
        player.setETimes(0);
    }

    public SpinResponse generateResponse(List<Scene> scenes, boolean finish, double betAfterScore) {
        Scene scene = scenes.get(scenes.size() - 1);
        Scene firstScene = scenes.get(0);
        double totalWin = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
        double freeGameWin = totalWin - firstScene.getGold();
        FlowData flowData = this.table.getGameService().initFlowData();
        List<String> actions = new ArrayList<>();
        actions.add(BGAMING_COMMAND_INIT);
        actions.add(BGAMING_COMMAND_SPIN);
        if (scene.getOpenFreeNum() > 0) {
            flowData.setState(BGAMING_COMMAND_FREE_SPIN);
        } else {
            if (scene.getType() == 1) {
                flowData.setState(finish ? BGAMING_STATE_CLOSED : BGAMING_COMMAND_FREE_SPIN);
            } else {
                flowData.setState(BGAMING_STATE_CLOSED);
            }
        }
        if (scene.getRespinCount() > 0) {
            flowData.setState(BGAMING_COMMAND_RE_SPIN);
        }
        flowData.setAvailable_actions(actions);

        int freeSpinIssued = checkIssued(scene);
        int freeSpinLeft = checkFreeSpinLeft(scene);
        OutCome outCome = new OutCome();
        outCome.setSpan_indices(scene.getNameIdxes());
        outCome.setScatters(castScatter(scene));
        outCome.setWin_lines(checkWins(scene.getPrizeDetail()));
        outCome.setFreespins_left(freeSpinLeft);
        outCome.setFreespins_performed(freeSpinIssued - freeSpinLeft);
        outCome.setFreespins_wins_sum(DecimalUtil.getBigDecimal2(freeGameWin * SUB_UNITS));
        outCome.setState(flowData.getState());
        outCome.setAction(BGAMING_COMMAND_SPIN);
        outCome.setCoins(scene.getCoins());
        outCome.setCoins_new(scene.getCoins_new());
        outCome.setCoins_respins(scene.getRespinCount());

        JSONObject bets = new JSONObject();
        if (scene.getRespinCount() > 0 || scene.getType() == 2) {
            bets.put("previous_lines", firstScene.getBetLine());
        } else {
            bets.put("lines", firstScene.getBetLine());
        }
        SpinResponse gameResponse = new SpinResponse();
        gameResponse.setGame(outCome);
        gameResponse.setBalance(DecimalUtil.getBigDecimal2(betAfterScore * SUB_UNITS));
        gameResponse.setAvailable_commands(actions);
        gameResponse.setBets(bets);
        gameResponse.setResult(castResult(scene, totalWin, finish, freeGameWin));
        return gameResponse;
    }

    private ResultDto castResult(Scene scene, double totalWin, boolean finish, double freeGameWin) {
        ResultDto resultDto = new ResultDto();
        resultDto.setScatters(scene.getScatterWin().multiply(BigDecimal.valueOf(SUB_UNITS)));
        resultDto.setTotal(DecimalUtil.getBigDecimal2(scene.getGold() * SUB_UNITS));
        resultDto.setRound_total_win(DecimalUtil.getBigDecimal2(totalWin * SUB_UNITS));
        resultDto.setLines(castLine(scene.getPrizeDetail()));
        if (finish) {
            resultDto.setFreespins_total_wins(DecimalUtil.getBigDecimal2(freeGameWin * SUB_UNITS));
            if (scene.getType() == 2) {
                resultDto.setTotal(DecimalUtil.getBigDecimal2(totalWin * SUB_UNITS));
                resultDto.setCoins_game(DecimalUtil.getBigDecimal2(scene.getGold() * SUB_UNITS));
            }
        }
        return resultDto;
    }

    private Map<Integer, BigDecimal> castLine(List<PrizeIcon> prizeDetail) {
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == SCATTER) continue;

            result.put(prizeIcon.getHitLine(), prizeIcon.getGold().multiply(BigDecimal.valueOf(SUB_UNITS)));
        }
        return result;
    }

    private JSONObject castScatter(Scene scene) {
        JSONObject result = new JSONObject();
        List<List<Integer>> indices = new ArrayList<>();
        int[][] rotary = scene.getRotary();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == SCATTER) {
                    List<Integer> indice = new ArrayList<>();
                    indice.add(i);
                    indice.add(i1);
                    indices.add(indice);
                }
            }
        }
        result.put("indices", indices);
        if (scene.getScatterWin().doubleValue() > 0) {
            result.put("factor", 1);
        }
        return result;
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

    private Map<Integer, List<Integer>> checkWins(List<PrizeIcon> prizeDetail) {
        Map<Integer, List<Integer>> result = new HashMap<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == SCATTER) continue;

            result.put(prizeIcon.getHitLine(), getWinColumnIds(prizeIcon.getPrizeIndex()));
        }
        return result;
    }

    private List<Integer> getWinColumnIds(Set<Integer> prizeIndex) {
        List<Integer> result = new ArrayList<>();
        for (Integer index : prizeIndex) {
            int x = index % COLUMNS;
            if (!result.contains(x)) {
                result.add(x);
            }
        }
        result.sort(Integer::compareTo);
        return result;
    }

    private List<Integer> castColumnIdx(Set<Integer> prizeIndex) {
        List<Integer> index = new ArrayList<>();
        int[] winIdx = new int[prizeIndex.size()];
        for (Integer idx : prizeIndex) {
            winIdx[idx % COLUMNS] = idx / COLUMNS;
        }
        Arrays.stream(winIdx).forEach(index::add);
        return index;
    }

    private JSONObject checkSpecialSymbol(int[][] rotary) {
        JSONObject result = new JSONObject();
        List<List<Integer>> indexes = new ArrayList<>();
        List<List<Integer>> scatterIndexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int icon = rotary[i][i1];
                if (icon == WILD) {
                    List<Integer> index = new ArrayList<>();
                    index.add(i1);
                    index.add(i);
                    indexes.add(index);
                }

                if (icon == SCATTER) {
                    List<Integer> index = new ArrayList<>();
                    index.add(i1);
                    index.add(i);
                    scatterIndexes.add(index);
                }
            }
        }
        if (!indexes.isEmpty()) {
            Map<Integer, List<List<Integer>>> inner = new HashMap<>();
            inner.put(WILD, indexes);
            result.put("wild", inner);
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

    private List<Scene> getResultScene(double betScore, double fac) {
        fac = fac < 1 ? Math.pow(fac, 3) : fac;
        List<Scene> result = new ArrayList<>();
        long now = TimeUtil.getNow();
        int type = 0;
        int freeNum = 0;
        int number = 0;
        int totalFreeNum = 0;
        double factor = fac;
        do {
            if (type == 1) {
                factor = fac * 0.45;
            }
            Scene sceneIconVo = generatedScene(betScore, factor, type);
            checkAndSetFreeInfo(sceneIconVo, betScore);
            int openFreeNum = sceneIconVo.getOpenFreeNum();
            totalFreeNum += openFreeNum;
            sceneIconVo.setOrder(nextId(now));
            sceneIconVo.setFreeNum(freeNum);
            sceneIconVo.setNumber(number++);
            sceneIconVo.setTotalFreeNum(totalFreeNum);
            result.add(sceneIconVo);

            if (sceneIconVo.getRespinCount() > 0) {
                List<Scene> respinScene = generateRespin(sceneIconVo, betScore, factor * 0.56);
                result.addAll(respinScene);
            }
            if (sceneIconVo.getOpenFreeNum() > 0) {
                freeNum += openFreeNum;
                type = 1;
            }
            if (sceneIconVo.getType() == 1) {
                freeNum--;
            }
        } while (freeNum > 0);

        return result;
    }

    private List<Scene> generateRespin(Scene sceneIconVo, double betScore, double factor) {
        List<Scene> result = new ArrayList<>();
        int respinCount = sceneIconVo.getRespinCount();
        int[][] coins = copyCoins(sceneIconVo.getCoins());
        int[][] rotary = copyRotary(sceneIconVo.getRotary());
        int totalMul = sumCoinsMul(coins);
        List<Integer> canInstallIndex = getCanInstallIndex(coins);
        List<List<Integer>> totalCoinsNew = new ArrayList<>();
        while (respinCount > 0) {
            respinCount--;
            Scene respin = new Scene();
            respin.setType(2);

            respin.setNameIdxes(sceneIconVo.getNameIdxes());
            int installNewSize = getInstallNewCoinSize(factor);
            ;
            int min = Math.min(installNewSize, canInstallIndex.size());
            if (canInstallIndex.size() - min < 2) {
                min = 0;
            }
            boolean win = false;
            for (int i = 0; i < min; i++) {
                int coinMul = getCoinMul();
                if (RandomUtil.nextDouble() < factor * 30 / (totalMul + coinMul)) {
                    Integer index = canInstallIndex.remove(RandomUtil.nextInt(canInstallIndex.size()));
                    int y = index / COLUMNS;
                    int x = index % COLUMNS;
                    respin.getCoins_new().add(Arrays.asList(y, x, coinMul));
                    coins[y][x] = coinMul;
                    rotary[x][y] = COIN;
                    win = true;
                }
            }
            if (win) {
                respinCount = 3;
            }
            respin.setRotary(copyRotary(rotary));
            respin.setRespinCount(respinCount);
            respin.setCoins(copyCoins(coins));
            result.add(respin);
            totalCoinsNew.addAll(respin.getCoins_new());
            respin.setCoins_new_total(new ArrayList<>(totalCoinsNew));
            if (canInstallIndex.isEmpty()) {
                respin.setRespinCount(0);
                break;
            }
        }

        Scene lastScene = result.get(result.size() - 1);
        totalMul = sumCoinsMul(lastScene.getCoins());
        lastScene.setMul(totalMul);
        lastScene.setGold(DecimalUtil.getBigDecimal2(totalMul * betScore).doubleValue());
        return result;
    }

    private int getInstallNewCoinSize(double factor) {
        double ran = RandomUtil.nextDouble() * factor;
        if (ran > 0.95) {
            return 3;
        } else if (ran > 0.86) {
            return 2;
        } else if (ran > 0.5) {
            return 1;
        }
        return 0;
    }

    private List<Integer> getCanInstallIndex(int[][] coins) {
        List<Integer> canInstallCoins = new ArrayList<>();
        for (int i = 0; i < COLUMNS; i++) {
            for (int i1 = 0; i1 < ROWS; i1++) {
                if (coins[i][i1] > 0) continue;

                canInstallCoins.add(i * COLUMNS + i1);
            }
        }
        return canInstallCoins;
    }

    private int sumCoinsMul(int[][] coins) {
        int totalMul = 0;
        for (int i = 0; i < COLUMNS; i++) {
            for (int i1 = 0; i1 < ROWS; i1++) {
                if (coins[i][i1] > 0) {
                    totalMul += coins[i][i1];
                }
            }
        }
        return totalMul;
    }

    private static int[][] copyRotary(int[][] rotary) {
        int[][] arr = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            arr[i] = Arrays.copyOf(rotary[i], COLUMNS);
        }
        return arr;
    }

    private static int[][] copyCoins(int[][] coins) {
        int[][] arr = new int[COLUMNS][ROWS];
        for (int i = 0; i < COLUMNS; i++) {
            arr[i] = Arrays.copyOf(coins[i], ROWS);
        }
        return arr;
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
            int mul = getMul(SCATTER, scatterIndexes.size());
            double scatterWinGold = betScore * mul;
            prizeIcon.setIcon(SCATTER);
            prizeIcon.setMul(mul);
            prizeIcon.setGold(DecimalUtil.getBigDecimal2(scatterWinGold));
            prizeIcon.setHitLine(-1);
            prizeIcon.setPrizeIndex(new HashSet<>(scatterIndexes));
            prizeIcon.setLine(LotteryConfig.FREE_NUM);
            sceneIconVo.getPrizeDetail().add(prizeIcon);
            sceneIconVo.setGold(DecimalUtil.getBigDecimal2(sceneIconVo.getGold() + scatterWinGold).doubleValue());
            sceneIconVo.getPrizeIndex().addAll(scatterIndexes);
            sceneIconVo.setOpenFreeNum(sceneIconVo.getType() == 0 ? LotteryConfig.FREE_NUM : LotteryConfig.FREE_TO_FREE_NUM);
            sceneIconVo.setScatterWin(DecimalUtil.getBigDecimal2(scatterWinGold));
        }
    }

    /**
     * 根据概率生成一些长的中奖线
     */
    private static boolean sceneLoneLines(int[][] rotary, double random, int[] nameIndexes) {
        double ran = random > 1.05 ? random : Math.pow(random, 2);
        if (RandomUtil.nextDouble() <= LotteryConfig.LONG_LINES_PRO * ran) {
            int[] prizeLine = PRIZE_LINE[RandomUtil.nextInt(PRIZE_LINE.length)];
            //随机找一个图标
            int icon = LotteryConfig.ICONS_WITH_MULTIPLE[RandomUtil.nextInt(ICONS_WITH_MULTIPLE.length)];

            for (int i = 0; i < prizeLine.length; i++) {
                if (i == 2) continue;

                int startIdx = ReelConfig.getRowIndexIconStartIdx(i, prizeLine[i] / COLUMNS, icon);
                nameIndexes[i] = startIdx;
                int[] rowIcons = ReelConfig.getRowIcons(0, startIdx, i);
                for (int i1 = 0; i1 < ROWS; i1++) {
                    rotary[i1][i] = rowIcons[i1];
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 生成场景
     */
    private static Scene generatedScene(double betScore, double factor, int type) {
        Scene scene = new Scene();
        scene.setDoubleMul(1);
        scene.setType(type);
        int[] nameIndexes = new int[COLUMNS];
        Arrays.fill(nameIndexes, -1);

        int[][] rotary = getInitRotary();
        boolean installLongLine = false;
        if (type == 0) {
            installLongLine = sceneLoneLines(rotary, factor, nameIndexes);
        }
        if (!installLongLine) {
            int scatterSize = getScatterSize(factor);
            installScatter(type, rotary, scatterSize, nameIndexes);

            if (type == 0) {
                int coinsSize = getCoinsSize();
                installCoins(rotary, coinsSize, nameIndexes);
            }
        }
        int wildSize = LotteryConfig.getWildSize(type);
        setWildIcon(rotary, wildSize, type, nameIndexes);

        int freeIcon = getRandomNormalIcon(0.4 * factor);
        //随机填充1，2，4，5个转轴的图标
        for (int i = 0; i < COLUMNS; i++) {
            if (type == 0 && i == 2) continue;

            if (rotary[0][i] == -1) {
                int startIndex;
                if (bigIconColumn(type, i)) {
                    startIndex = ReelConfig.getRanNameIndex(type, i, freeIcon);
                    int[] rowIcons = ReelConfig.getRowIcons(type, startIndex, i);
                    for (int j = 1; j < COLUMNS - 1; j++) {
                        nameIndexes[j] = startIndex;
                        for (int i1 = 0; i1 < ROWS; i1++) {
                            rotary[i1][j] = rowIcons[i1];
                        }
                    }
                    i = COLUMNS - 2;
                    continue;
                } else {
                    int normalIcon = getRandomNormalIcon(1);
                    startIndex = ReelConfig.getRowIndexIconStartIdx(i, -1, normalIcon);
                }
                nameIndexes[i] = startIndex;
                int[] rowIcons = ReelConfig.getRowIcons(type, startIndex, i);
                for (int i1 = 0; i1 < ROWS; i1++) {
                    rotary[i1][i] = rowIcons[i1];
                }
            }
        }

        List<Integer> useIcons = arrToList();
        Map<Integer, Integer> allPrizeMaps = new HashMap<>();
        for (int i = 0; i < ROWS; i++) {
            if (rotary[i][2] == -1) {
                Map<Integer, Integer> prizeMaps = getPrize(rotary, i * COLUMNS + 2);
                if (!prizeMaps.isEmpty()) {
                    allPrizeMaps.putAll(prizeMaps);
                    List<Integer> icons = new ArrayList<>(prizeMaps.keySet());
                    Integer icon = icons.get(RandomUtil.nextInt(icons.size()));
                    double mul = prizeMaps.get(icon) * scene.getDoubleMul() * 1.0D / BASE_LINE;
                    double tmpRan = factor > 1 ? factor : Math.pow(factor, 3);
                    double tempFactor = type == 0 ? tmpRan * SMALL_WIN_PRO : tmpRan * FREE_SMALL_WIN_PRO;
                    if (mul >= 100) { // 不干预100以上倍数
                        tempFactor = tmpRan;
                    }
                    // 降低小奖概率
                    if (mul <= 1) {
                        tempFactor *= 0.033780182;
                    }
                    if (tempFactor / mul >= RandomUtil.nextDouble()) {
                        int startIdx = ReelConfig.getRowIndexIconStartIdx(2, i, icon);
                        int[] rowIcons = ReelConfig.getRowIcons(0, startIdx, 2);
                        for (int i1 = 0; i1 < ROWS; i1++) {
                            rotary[i1][2] = rowIcons[i1];
                        }
                        nameIndexes[2] = startIdx;
                        break;
                    }
                }
            }
        }
        if (rotary[0][2] == -1) {
            List<Integer> temp = new ArrayList<>(useIcons);
            if (!allPrizeMaps.isEmpty()) {
                allPrizeMaps.keySet().forEach(temp::remove);
            }
            if (temp.isEmpty()) {//如果所有图标都有可能中奖 就随机给一个图标
                temp = new ArrayList<>(useIcons);
            }
            Integer icon = temp.get(RandomUtil.nextInt(temp.size()));
            int startIdx = ReelConfig.getRowIndexIconStartIdx(2, 0, icon);
            int[] rowIcons = ReelConfig.getRowIcons(0, startIdx, 2);
            for (int i1 = 0; i1 < ROWS; i1++) {
                rotary[i1][2] = rowIcons[i1];
            }
            nameIndexes[2] = startIdx;
        }
        scene.setNameIdxes(catArrToList(nameIndexes));

        scene.setRotary(rotary);
        setCoinsData(scene);
        setMulWithScene(scene, betScore);
        return scene;
    }

    private static void installCoins(int[][] rotary, int coinsSize, int[] nameIndexes) {
        if (coinsSize == 0) return;

        List<Integer> canUseColumnIds = new ArrayList<>();
        for (int i = 0; i < COLUMNS; i++) {
            if (rotary[0][i] != -1) continue;

            canUseColumnIds.add(i);
        }

        if (!canUseColumnIds.isEmpty()) {
            int avg = coinsSize / canUseColumnIds.size();
            int max = Math.max(RandomUtil.nextInt(1, 4), avg);
            Collections.shuffle(canUseColumnIds);

            for (Integer canUseColumnId : canUseColumnIds) {
                List<Integer> indexes = ReelConfig.getIconRowNameIndex(0, canUseColumnId, COIN);
                if (canUseColumnId == 3) {
                    max = 2;
                }
                int startIdx;
                if (max == 3) {
                    startIdx = indexes.get(0);
                } else if (max == 2) {
                    startIdx = indexes.get(1);
                } else {
                    startIdx = indexes.get(indexes.size() - 1);
                }
                int[] rowIcons = ReelConfig.getRowIcons(0, startIdx, canUseColumnId);
                for (int i1 = 0; i1 < ROWS; i1++) {
                    rotary[i1][canUseColumnId] = rowIcons[i1];
                }
                nameIndexes[canUseColumnId] = startIdx;
                coinsSize -= max;
                if (coinsSize <= 0) break;
            }
        }
    }

    private static void setCoinsData(Scene scene) {
        int[][] coins = new int[COLUMNS][ROWS];
        List<List<Integer>> coinsNew = new ArrayList<>();
        int[][] rotary = scene.getRotary();
        boolean hasCoin = false;
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int icon = rotary[i][i1];
                if (icon == COIN) {
                    int coinMul = getCoinMul();
                    coins[i1][i] = coinMul;
                    coinsNew.add(Arrays.asList(i1, i, coinMul));
                    hasCoin = true;
                }
            }
        }
        if (hasCoin) {
            scene.setCoins(coins);
            scene.setCoins_new(coinsNew);
            if (coinsNew.size() >= 6) {
                scene.setRespinCount(3);
            }
        }
    }

    private static List<Integer> catArrToList(int[] nameIndexes) {
        List<Integer> result = new ArrayList<>();
        for (int nameIndex : nameIndexes) {
            result.add(nameIndex);
        }
        return result;
    }

    private static boolean bigIconColumn(int type, int columnId) {
        return type == 1 && columnId > 0 && columnId < COLUMNS - 1;
    }

    private static void installScatter(int type, int[][] rotary, int scatterSize, int[] nameIndexes) {
        if (scatterSize == 0) return;

        List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 2, 4));
        if (type == 1) {
            canUseColumnIds.remove((Integer) 2);
            scatterSize = Math.min(2, scatterSize);
        }
        for (int i = 0; i < scatterSize; i++) {
            Integer index = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
            int startIdx = ReelConfig.getRowIndexIconStartIdx(index, -1, SCATTER);
            int[] rowIcons = ReelConfig.getRowIcons(type, startIdx, index);
            for (int i1 = 0; i1 < ROWS; i1++) {
                rotary[i1][index] = rowIcons[i1];
            }
            nameIndexes[index] = startIdx;
        }
    }

    private static void setWildIcon(int[][] rotary, int wildSize, int type, int[] nameIndexes) {
        if (wildSize == 0) return;

        if (type == 0) {
            List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 1, 3, 4));
            for (int i = 0; i < wildSize; i++) {
                Integer columnId = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
                if (rotary[0][columnId] != -1) continue;

                int startIdx = ReelConfig.getRanNameIndex(0, columnId, WILD);
                int[] rowIcons = ReelConfig.getRowIcons(0, startIdx, columnId);
                for (int i1 = 0; i1 < ROWS; i1++) {
                    rotary[i1][columnId] = rowIcons[i1];
                }
                nameIndexes[columnId] = startIdx;
            }
        } else {
            List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 1, 4));
            Integer columnId = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
            if (columnId == 1) {
                int startIdx = ReelConfig.getRanNameIndex(1, columnId, WILD);
                for (int i = 1; i < COLUMNS - 1; i++) {
                    for (int i1 = 0; i1 < ROWS; i1++) {
                        rotary[i1][i] = WILD;
                    }
                    nameIndexes[i] = startIdx;
                }
            } else {
                int startIndex = ReelConfig.getRanNameIndex(type, columnId, WILD);
                int[] rowIcons = ReelConfig.getRowIcons(1, startIndex, columnId);
                for (int i1 = 0; i1 < ROWS; i1++) {
                    rotary[i1][columnId] = rowIcons[i1];
                }
                nameIndexes[columnId] = startIndex;
            }
        }
    }

    /**
     * @return 返回所有的普通图标集合
     */
    private static List<Integer> arrToList() {
        List<Integer> list = new ArrayList<>();
        for (int i : ICONS_WITH_MULTIPLE) {
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
        for (int i = 0; i < PRIZE_LINE.length; i++) {
            int[] prizeLine = PRIZE_LINE[i];
            for (int i1 : prizeLine) {
                if (i1 == index) {
                    int[] lineIcons = checkLineIcon(prizeLine, rotary);
                    PrizeIcon prizeIcon = checkPrize(lineIcons, prizeLine, i);
                    if (prizeIcon != null && prizeIcon.getIcon() != SCATTER) {
                        int icon = prizeIcon.getIcon();
                        int line = prizeIcon.getLine();
                        int mul = getMul(icon, line);
                        if (!prizeMaps.containsKey(icon)) {
                            prizeMaps.put(icon, mul);
                        } else {
                            prizeMaps.put(icon, prizeMaps.get(icon) + mul);
                        }
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
            if (current == WILD || current == -1) {
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
        if (icon != null && count >= 3 && icon != COIN) {
            Set<Integer> pos = new HashSet<>();
            for (int i = 0; i < count; i++) {
                pos.add(prizeLine[i]);
            }
            return new PrizeIcon(icon, hitLine, count, pos);
        }
        return null;
    }

    private static int[] checkLineIcon(int[] prizeLine, int[][] rotary) {
        int[] lineIcons = new int[COLUMNS];
        for (int i = 0; i < prizeLine.length; i++) {
            int index = prizeLine[i];
            int icon = rotary[index / COLUMNS][index % COLUMNS];
            lineIcons[i] = icon;
        }
        return lineIcons;
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

    /**
     * 设置场景中奖倍数和中奖坐标
     *
     * @param scene 场景
     */
    private static void setMulWithScene(Scene scene, double betScore) {
        int[][] rotary = scene.getRotary();
        for (int i = 0; i < PRIZE_LINE.length; i++) {
            int[] prizeLine = PRIZE_LINE[i];
            int[] lineIcons = checkLineIcon(prizeLine, rotary);
            PrizeIcon prizeIconVo = checkPrize(lineIcons, prizeLine, i);
            if (prizeIconVo != null && prizeIconVo.getIcon() != SCATTER) {
                scene.getPrizeDetail().add(prizeIconVo);
                int mul = getMul(prizeIconVo.getIcon(), prizeIconVo.getLine());
                prizeIconVo.setMul(mul);
                double gold = betScore * mul / BASE_LINE;
                prizeIconVo.setGold(DecimalUtil.getBigDecimal2(gold));
                prizeIconVo.getPrizeIndex().forEach(l -> scene.getPrizeIndex().add(l));
                scene.setGold(DecimalUtil.getBigDecimal2(scene.getGold() + gold).doubleValue());
            }
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
        List<Scene> sceneIconVos = getResultScene(betScore, factor);
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
