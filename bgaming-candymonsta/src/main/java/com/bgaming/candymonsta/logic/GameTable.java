package com.bgaming.candymonsta.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.candymonsta.config.LotteryConfig;
import com.bgaming.candymonsta.entity.PrizeIcon;
import com.bgaming.candymonsta.entity.Scene;
import com.bgaming.candymonsta.entity.dto.GameFeatures;
import com.bgaming.candymonsta.entity.dto.OutCome;
import com.bgaming.candymonsta.entity.dto.RoundDetailDto;
import com.bgaming.candymonsta.entity.dto.SpinResponse;
import com.bgaming.candymonsta.utils.DateTimeUtil;
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

import static com.bgaming.candymonsta.config.LotteryConfig.*;
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
            Double stake = options.getDouble(BET);
            if (environmentCheck(player, userId)) return null;

            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            if (cheatingDetection(player, stake)) return null;

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}", player.getUser().getUserID(), stake);
                return null;
            }

            List<Scene> scenes = getScenes(player);
            if (command.equals("freespin")) {
                int times = player.getETimes();
                if (scenes == null || scenes.isEmpty() || times + 1 >= scenes.size()) {
                    log.error("userId {} , error request freeSpin1, scene == null", userId);
                    return null;
                }

                if (isErrorRequest(scenes, stake,times)) {
                    log.error("userId {} , error request freeSpin2, scene == null", userId);
                    return null;
                }
                times++;
                SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue(), beforeScore, times);
                player.setETimes(times);
                log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                return response;
            } else {
                if (isErrorSpinReq(scenes)) {
                    log.error("userId {} , error request spin, scenes {}", userId, JSONObject.toJSONString(scenes));
                    return null;
                }
            }
            double orderStake = stake;
            if (notEnoughGold(orderStake, beforeScore)) {
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
            SpinResponse response = getSpinResponse(player, orderStake, scenes, stake, beforeScore, 0);
            log.info("玩家 {}  数据 result {}", player.getUserId(), response);
            return response;
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }

    private SpinResponse getSpinResponse(Player player, double orderStake, List<Scene> scenes, Double stake, double beforeScore, int times) {
        double winGold = scenes.get(times).getGold();
        setCurData(player, orderStake, winGold);
        Scene scene = scenes.get(times);
        scene.setBetScore(DecimalUtil.getBigDecimal2(orderStake).doubleValue());
        scene.setBetScoreServer(DecimalUtil.getBigDecimal2(stake).doubleValue());
        String pOrder = player.getUser().getUserID() + "-" + TimeUtil.getNow();
        scene.setBeforeScore(DecimalUtil.getBigDecimal2(beforeScore).doubleValue());
        if (times > 0) {
            pOrder = scenes.get(0).getPOrder();
            String order = scenes.get(0).getOrder();
            scene.setOrder(order);
            scene.setAfterScore(DecimalUtil.getBigDecimal2(player.getUser().getScore()).doubleValue());
        } else {
            scene.setAfterScore(DecimalUtil.getBigDecimal2(beforeScore - orderStake).doubleValue());
        }
        player.getExtendJson().put("pOrder", pOrder);
        scene.setPOrder(pOrder);

        boolean finish = (scene.getType() == 0 && scene.getOpenFreeNum() == 0)
                || (scene.getType() == 1 && scene.getFreeNum() == 1 && scene.getOpenFreeNum() == 0);
        JSONObject extendData = getExtendString(player, scene.getPOrder(), finish);
        extendData.put(FREE_TYPE, scene.getFreeType());
        extendData.put(BUY_TYPE, 0);
        extendData.put(BET_TYPE, 0);
        SpinResponse response = generateResponse(scenes.subList(0,times + 1), finish, DecimalUtil.getBigDecimal2(scenes.get(0).getAfterScore()).doubleValue());
        List<RoundDetailDto> roundDetailDtos = new ArrayList<>();
        if (finish) {
            for (Scene tmpScene : scenes) {
                RoundDetailDto roundDetailDto = generateRoundDetail(tmpScene.getBeforeScore(), player, tmpScene);
                roundDetailDtos.add(roundDetailDto);
            }
        } else {
            RoundDetailDto roundDetailDto = generateRoundDetail(beforeScore, player, scene);
            roundDetailDtos.add(roundDetailDto);
        }
        sendServerMsg(player, beforeScore, orderStake, winGold, roundDetailDtos, extendData, finish, scene.getNumber());
        player.getExtendJson().put("spinResponse", response);
        if (finish) {
            player.getExtendJson().remove(SCENE);
            player.getExtendJson().remove("spinResponse");
        }
        return response;
    }

    private boolean isErrorSpinReq(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) return false;

        Scene lastScene = scenes.get(scenes.size() - 1);
        if (lastScene.getOpenFreeNum() > 0) return true;

        if (lastScene.getType() == 1) {
            if (lastScene.getFreeNum() > 1) {
                return true;
            }
        }

        return false;
    }

    private static boolean isErrorRequest(List<Scene> scenes, Double stake,int times) {
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
        roundDetailDto.setProfitText(realProfit.stripTrailingZeros().toPlainString());
        roundDetailDto.setProfit(realProfit);
        roundDetailDto.setCurrency(player.getCoinsType());
        roundDetailDto.setBalanceBeforeText(DecimalUtil.getBigDecimal2(beforeScore).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceBefore(DecimalUtil.getBigDecimal2(beforeScore));
        roundDetailDto.setBalanceAfterText(DecimalUtil.getBigDecimal2(player.getUser().getScore()).stripTrailingZeros().toPlainString());
        roundDetailDto.setBalanceAfter(DecimalUtil.getBigDecimal2(player.getUser().getScore()));
        roundDetailDto.setWinLines(castDetailWinLine(prizeDetail));
        if (scene.getScatterWin() != null && scene.getScatterWin().doubleValue() > 0) {
            roundDetailDto.setScatterWin(scene.getScatterWin());
            roundDetailDto.setScatterWinText(scene.getScatterWin().stripTrailingZeros().toPlainString());
        }
        roundDetailDto.setSymbols(castDetailSymbol(rotary, scene.getHoldWildIndexes()));
        return roundDetailDto;
    }

    private List<List<String>> castDetailWinLine(List<PrizeIcon> prizeDetail) {
        List<List<String>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == SCATTER) continue;

            String line = String.valueOf(prizeIcon.getLine()).concat("x");
            String iconStr = SYMBOL_NAME[prizeIcon.getIcon()];
            String lineIdEndPos = "Line ".concat(String.valueOf(prizeIcon.getHitLine() + 1)).concat(" - ").concat(prizeIcon.getGold().stripTrailingZeros().toPlainString());
            List<String> winLine = Arrays.asList(line, iconStr, lineIdEndPos);
            result.add(winLine);
        }
        return result;
    }

    private List<String> castDetailSymbol(int[][] rotary, List<Integer> holdWildIndexes) {
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (holdWildIndexes.contains(i * COLUMNS + i1)) {
                    symbols.add(SYMBOL_NAME[WILD]);
                } else {
                    int icon = rotary[i][i1];
                    symbols.add(SYMBOL_NAME[icon]);
                }
            }
        }
        return symbols;
    }

    private static final String[] SYMBOL_NAME = {"h1", "m1", "m2", "m3", "m4", "m5", "m6", "l1", "l2", "l3", "l4", "l5", "scatter", "wild"};

    private void resetPlayerExt(Player player) {
        player.setETimes(0);
    }

    public SpinResponse generateResponse(List<Scene> scenes, boolean finish, double betAfterScore) {
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

        int freeSpinIssued = checkIssued(scene);
        int freeSpinLeft = checkFreeSpinLeft(scene);
        GameFeatures gameFeatures = new GameFeatures();
        gameFeatures.setFreespins_issued(freeSpinIssued);
        gameFeatures.setFreespins_left(freeSpinLeft);
        gameFeatures.setSticky_wilds(castPosition(scene.getHoldWildIndexes()));

        OutCome outCome = new OutCome();
        outCome.setBet(DecimalUtil.getBigDecimal2(betScore * SUB_UNITS));
        outCome.setWin(DecimalUtil.getBigDecimal2(scene.getGold() * SUB_UNITS));
        outCome.setSpecial_symbols(checkSpecialSymbol(scene.getRotary()));
        outCome.setWins(checkWins(scene.getPrizeDetail()));
        outCome.setScreen(castReel(scene.getRotary()));
        outCome.setFreespins_issued(scene.getOpenFreeNum() == 0 ? null : scene.getOpenFreeNum());

        SpinResponse gameResponse = new SpinResponse();
        gameResponse.setApi_version(this.table.getGameService().getBaseVersion().getApiVersion());
        gameResponse.setBalance(balance);
        gameResponse.setFlow(jsonObject);
        gameResponse.setOutcome(outCome);
        gameResponse.setFeatures(gameFeatures);
        return gameResponse;
    }

    private List<List<Integer>> castPosition(List<Integer> holdWildIndexes) {
        List<List<Integer>> result = new ArrayList<>();
        for (Integer holdWildIndex : holdWildIndexes) {
            List<Integer> position = new ArrayList<>();
            position.add(holdWildIndex % COLUMNS);
            position.add(holdWildIndex / COLUMNS);
            result.add(position);
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
                win.add("line");
                win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(SUB_UNITS)));
                win.add(castColumnIdx(prizeIcon.getPrizeIndex()));
                win.add(prizeIcon.getHitLine());
            }
            result.add(win);
        }
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

    private void sendServerMsg(Player player, double beforeScore, double betScore, double winGold, List<RoundDetailDto> gameDetail, JSONObject extData, boolean finish, int number) {
        List<Scene> scenes = getScenes(player);
        player.initBetId(gameInfo.getRoomID(), scenes.get(0).getOrder());
        player.setBetIdNum(number);
        double finishBeforeScore = finish ? gameDetail.get(0).getBalanceBefore().doubleValue() : beforeScore;
        this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(finishBeforeScore).doubleValue(), this.gameInfo,
                betScore, winGold, number, scenes.get(0).getPOrder(), extData, finish ? 1 : 0, false);
        if (finish) {
            log.info("userid = {},发送完整注单", player.getUser().getUserID());
        } else {
            log.info("userid = {},发送单场注单", player.getUser().getUserID());
        }
        sendDataLog(player, gameDetail, finish,winGold);
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
     * @param player  当前玩家
     * @param finish
     */
    private void sendDataLog(Player player, Object gameDetail, boolean finish, double gold) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送es日志时，服务器发生错误");
            return;
        }

        double settleBet = scenes.get(0).getBetScore();
        double betScore = scenes.get(0).getBetScoreServer();
        String pOrder = scenes.get(0).getPOrder();
        JSONObject jObj = new JSONObject();
        jObj.put(ICON_DATA, JSONObject.toJSONString(gameDetail));
        jObj.put(UUID, TimeUtil.getNow());
        jObj.put(BET_MUL, player.getEMul());
        jObj.put(PARENT_ORDER, pOrder);
        sendLogData(player, DecimalUtil.getBigDecimal2(player.getUser().getScore() - gold + settleBet).doubleValue(), settleBet, gold, pOrder, finish ? 1 : 0, jObj, betScore);
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

    private List<Scene> getResultScene(double betScore, double factor) {
        List<Scene> result = new ArrayList<>();
        long now = TimeUtil.getNow();
        int type = 0;
        int freeNum = 0;
        int number = 0;
        int totalFreeNum = 0;
        HashSet<Integer> holdWildIndexes = new HashSet<>();
        do {
            Scene sceneIconVo = generatedScene(betScore, factor, type, holdWildIndexes);
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
            result.add(sceneIconVo);
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
            sceneIconVo.setOpenFreeNum(LotteryConfig.FREE_NUM);
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
    private static Scene generatedScene(double betScore, double factor, int type, HashSet<Integer> holdWildIndexes) {
        Scene scene = new Scene();
        scene.setDoubleMul(1);
        scene.setType(type);
        int[][] rotary = getInitRotary();
        sceneLoneLines(rotary, type == 1 ? factor * 0.1 : factor);
        int scatterSize = getScatterSize(type == 1 ? 0.5 * factor : factor);
        installScatter(rotary, scatterSize);
        double fixedSmallIcon = type == 1 ? 0.75 : 0.6886;
        //随机填充1，2，4，5个转轴的图标
        for (int i = 0; i < COLUMNS; i++) {
            if (i == 2) continue;

            for (int i1 = 0; i1 < ROWS; i1++) {
                if (rotary[i1][i] == -1) {
                    int icon;
                    if (i == 0 && RandomUtil.nextDouble() < fixedSmallIcon) {
                        icon = RandomUtil.nextInt(7, 12);
                    } else {
                        icon = LotteryConfig.getRandomNormalIcon();
                    }
                    rotary[i1][i] = icon;
                }
            }
        }
        int wildSize = LotteryConfig.getWildSize(type);
        List<Integer> wildIndex = setWildIcon(rotary, wildSize, type);
        for (Integer index : wildIndex) {
            if (holdWildIndexes.contains(index)) continue;

            rotary[index / COLUMNS][index % COLUMNS] = WILD;
            if (type == 1) {
                holdWildIndexes.add(index);
            }
        }

        int[][] tmpRotary = copyRotary(rotary);
        if (type == 1) {
            for (Integer index : holdWildIndexes) {
                tmpRotary[index / COLUMNS][index % COLUMNS] = WILD;
            }
        }
        List<Integer> useIcons = arrToList();
        for (int i = 0; i < ROWS; i++) {
            if (rotary[i][2] == -1) {
                Map<Integer, Integer> prizeMaps = getPrize(tmpRotary, i * COLUMNS + 2);
                if (!prizeMaps.isEmpty()) {
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
                        tempFactor *= 0.33780182;
                    }
                    if (tempFactor / mul >= RandomUtil.nextDouble()) {
                        rotary[i][2] = icon;
                    }
                }
                if (rotary[i][2] == -1) {
                    List<Integer> temp = new ArrayList<>(useIcons);
                    if (!prizeMaps.isEmpty()) {
                        prizeMaps.keySet().forEach(temp::remove);
                    }
                    if (temp.isEmpty()) {//如果所有图标都有可能中奖 就随机给一个图标
                        temp = new ArrayList<>(useIcons);
                    }
                    rotary[i][2] = temp.get(RandomUtil.nextInt(temp.size()));
                }
            }
        }
        scene.setRotary(rotary);
        if(type == 1){
            scene.setHoldWildIndexes(new ArrayList<>(holdWildIndexes));
        }
        setMulWithScene(scene, betScore);
        return scene;
    }

    private static void installScatter(int[][] rotary, int scatterSize) {
        if (scatterSize == 0) return;

        List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        for (int i = 0; i < scatterSize; i++) {
            Integer index = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
            rotary[RandomUtil.nextInt(ROWS)][index] = SCATTER;
        }
    }

    private static int[][] copyRotary(int[][] rotary) {
        int[][] arr = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            arr[i] = Arrays.copyOf(rotary[i], COLUMNS);
        }
        return arr;
    }

    private static List<Integer> setWildIcon(int[][] rotary, int wildSize, int type) {
        if (wildSize == 0) return new ArrayList<>();

        return randomGoldenIcon(rotary, wildSize, type);
    }

    /**
     * [00,01,02,03,04]
     * [05,06,07,08,09]
     * [10,11,12,13,14]
     */
    private static List<Integer> randomGoldenIcon(int[][] rotary, int goldenSize, int type) {
        List<Integer> goldenIconIdx = new ArrayList<>();
        List<Integer> canUseGoldenIconIdx = new ArrayList<>();
        List<Integer> tmp;
        if (type == 0) {
            tmp = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14));
        } else {
            tmp = new ArrayList<>(Arrays.asList(1, 2, 3, 6, 7, 8, 11, 12, 13));
        }
        for (Integer iconIdx : tmp) {
            int icon = rotary[iconIdx / COLUMNS][iconIdx % COLUMNS];
            if (icon == SCATTER) continue;

            canUseGoldenIconIdx.add(iconIdx);
        }
        int size = Math.min(goldenSize, canUseGoldenIconIdx.size());
        for (int i = 0; i < size; i++) {
            Integer idx = canUseGoldenIconIdx.remove(RandomUtil.nextInt(canUseGoldenIconIdx.size()));
            goldenIconIdx.add(idx);
        }
        return goldenIconIdx;
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
        if (icon != null && count >= 3) {
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
        int[][] tmpRotary = copyRotary(rotary);
        if (scene.getType() == 1) {
            for (Integer index : scene.getHoldWildIndexes()) {
                tmpRotary[index / COLUMNS][index % COLUMNS] = WILD;
            }
        }
        for (int i = 0; i < PRIZE_LINE.length; i++) {
            int[] prizeLine = PRIZE_LINE[i];
            int[] lineIcons = checkLineIcon(prizeLine, tmpRotary);
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
