package com.bgaming.wildcashx9990.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.wildcashx9990.config.LotteryConfig;
import com.bgaming.wildcashx9990.entity.BonusData;
import com.bgaming.wildcashx9990.entity.PrizeIcon;
import com.bgaming.wildcashx9990.entity.Scene;
import com.bgaming.wildcashx9990.entity.dto.OutCome;
import com.bgaming.wildcashx9990.entity.dto.RoundDetailDto;
import com.bgaming.wildcashx9990.entity.dto.SpinResponse;
import com.bgaming.wildcashx9990.utils.DateTimeUtil;
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

import static com.bgaming.wildcashx9990.config.LotteryConfig.*;
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
            int userId = player.getUserId();
            Double stake = options.getDouble(BET);
            if (environmentCheck(player, userId)) return null;

            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            if (cheatingDetection(player, stake)) return null;

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {}  ", player.getUser().getUserID(), stake);
                return null;
            }

            int requestType = 0;
            if (options.containsKey(PURCHASED_FEATURE)) {
                String purchasedFeature = options.getString(PURCHASED_FEATURE);
                if (purchasedFeature.equals(PURCHASED_BONUS_SPIN)) {
                    requestType = 1;
                }
            }
            double beforeScore = player.getUser().getScore();
            stake = DecimalUtil.getBigDecimal2(stake / LotteryConfig.SUB_UNITS).doubleValue();
            double orderStake = DecimalUtil.getBigDecimal2(stake * REQUEST_TYPE_MUL[requestType]).doubleValue();

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
            List<Scene> scenes = getScenes(player);
            double changeScore = winGold - orderStake;
            setControlScore(player, changeScore);
            setCurData(player, orderStake, winGold);
            resetPlayerExt(player);
            Scene scene = scenes.get(0);
            scene.setBetScore(DecimalUtil.getBigDecimal2(orderStake).doubleValue());
            scene.setFreeType(requestType);
            scene.setBetScoreServer(DecimalUtil.getBigDecimal2(stake).doubleValue());
            JSONObject extendData = getExtendString(player, scene.getPOrder());
            extendData.put(FREE_TYPE, scene.getFreeType());
            extendData.put(BUY_TYPE, 0);
            extendData.put(LotteryConfig.BET_TYPE, 0);
            SpinResponse response = generateResponse(scenes, DecimalUtil.getBigDecimal2(beforeScore - orderStake).doubleValue());
            RoundDetailDto roundDetailDto = generateRoundDetail(response, beforeScore, player, scene);
            sendServerMsg(player, beforeScore, orderStake, winGold, roundDetailDto, extendData);
            log.info("玩家 {}  数据 result {}", player.getUserId(), response);
            return response;
        } catch (Exception var24) {
            log.error("userId {} , 开奖报错: ", player.getUser().getUserID(), var24);
        }
        return null;
    }

    private RoundDetailDto generateRoundDetail(SpinResponse response, double beforeScore, Player player, Scene scene) {
        int[][] rotary = scene.getRotary();
        List<PrizeIcon> prizeDetail = scene.getPrizeDetail();
        RoundDetailDto roundDetailDto = new RoundDetailDto();
        roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));
        roundDetailDto.setUsedFeature(scene.getFreeType() == 1);
        roundDetailDto.setFeatureName(featureNameByBetType(scene.getFreeType()));
        BigDecimal realBet = DecimalUtil.getBigDecimal2(response.getOutcome().getBet().doubleValue() / LotteryConfig.SUB_UNITS);
        BigDecimal realWin = DecimalUtil.getBigDecimal2(response.getOutcome().getWin().doubleValue() / LotteryConfig.SUB_UNITS);
        BigDecimal realProfit = DecimalUtil.getBigDecimal2(response.getOutcome().getWin().subtract(response.getOutcome().getBet()).doubleValue() / LotteryConfig.SUB_UNITS);
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
            roundDetailDto.setScatterWin(scene.getBonusWin());
            roundDetailDto.setScatterWinText(scene.getBonusWin().stripTrailingZeros().toPlainString());
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
            if (prizeIcon.getIcon() == SCATTER) continue;

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
                int icon = rotary[i][i1];
                symbols.add(SYMBOL_NAME[icon]);
            }
        }
        return symbols;
    }

    private static final String[] SYMBOL_NAME = {"h1", "h2", "l1", "l2", "l5", "l4", "l3", "l6", "bonus"};

    private void resetPlayerExt(Player player) {
        player.getExtendJson().remove("buyFree");
        player.getExtendJson().remove("playTimes");
        player.getExtendJson().remove(LotteryConfig.BET_TYPE);
    }

    public SpinResponse generateResponse(List<Scene> scenes, double betAfterScore) {
        Scene firstScene = scenes.get(0);
        double betScore = firstScene.getBetScore();
        String orderId = firstScene.getOrder();
        BgBalance balance = new BgBalance();
        balance.setGame(DecimalUtil.getBigDecimal2(this.totalWinGold * LotteryConfig.SUB_UNITS));
        balance.setWallet(DecimalUtil.getBigDecimal2(betAfterScore * LotteryConfig.SUB_UNITS));

        FlowData flowData = this.table.getGameService().initFlowData();
        flowData.setState(BGAMING_STATE_CLOSED);
        flowData.setCommand(BGAMING_COMMAND_SPIN);
        flowData.setRound_id(orderId);
        flowData.setLast_action_id(orderId + "_1");

        OutCome outCome = new OutCome();
        outCome.setBet(DecimalUtil.getBigDecimal2(betScore * LotteryConfig.SUB_UNITS));
        outCome.setWin(DecimalUtil.getBigDecimal2(this.totalWinGold * LotteryConfig.SUB_UNITS));
        outCome.setSpecial_symbols(checkSpecialSymbol(firstScene.getRotary()));
        outCome.setWins(checkWins(firstScene.getPrizeDetail()));
        outCome.setScreen(castReel(firstScene.getRotary()));

        SpinResponse gameResponse = new SpinResponse();
        gameResponse.setApi_version(this.table.getGameService().getBaseVersion().getApiVersion());
        gameResponse.setBalance(balance);
        gameResponse.setFlow(flowData);
        gameResponse.setOutcome(outCome);
        if (firstScene.getBonusData() != null) {
            JSONObject bonusData = new JSONObject();
            bonusData.put("bonus_data", firstScene.getBonusData());
            gameResponse.setFeatures(bonusData);
        }
        return gameResponse;
    }

    private List<List<String>> castReel(int[][] rotary) {
        List<List<String>> screen = new ArrayList<>();
        for (int i = 0; i < LotteryConfig.COLUMNS; i++) {
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
                win.add("scatter");
                win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(LotteryConfig.SUB_UNITS)));
                win.add(scatterIndex(prizeIcon.getPrizeIndex()));
                result.add(win);
                continue;
            }
            win.add("line");
            win.add(prizeIcon.getGold().multiply(BigDecimal.valueOf(LotteryConfig.SUB_UNITS)));
            win.add(castColumnIdx(prizeIcon.getPrizeIndex()));
            win.add(prizeIcon.getHitLine());
            result.add(win);
        }
        return result;
    }

    private Object scatterIndex(Set<Integer> prizeIndex) {
        List<int[]> scatterIndexes = new ArrayList<>();
        for (Integer index : prizeIndex) {
            int[] idx = new int[2];
            idx[0] = index % COLUMNS;
            idx[1] = index / COLUMNS;
            scatterIndexes.add(idx);
        }
        return scatterIndexes;
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
            for (int i1 = 0; i1 < COLUMNS; i1++) {
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
            inner.put(8, indexes);
            result.put("scatter", inner);
        }
        return result;
    }

    @Override
    public void usePrize(Player player, UsePrize usePrize) {

    }

    private void sendServerMsg(Player player, double beforeScore, double betScore, double winGold, Object gameDetail, JSONObject extData) {
        List<Scene> scenes = getScenes(player);
        player.initBetId(gameInfo.getRoomID(), scenes.get(0).getOrder());
        player.setBetIdNum(0);
        this.table.getGameService().getRabbitMqService().sendOrder(player, DecimalUtil.getBigDecimal2(beforeScore).doubleValue(), this.gameInfo, betScore, winGold, 0, scenes.get(0).getPOrder(), extData, 1, false);
        log.info("userid = {},发送完整注单", player.getUser().getUserID());
        sendDataLog(player, gameDetail);
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
    private void sendDataLog(Player player, Object gameDetail) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送es日志时，服务器发生错误");
            return;
        }

        double settleBet = scenes.get(0).getBetScore();
        double betScore = scenes.get(0).getBetScoreServer();
        double gold = this.totalWinGold;
        String pOrder = scenes.get(0).getPOrder();
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

        JSONObject prizeStatistics = this.getPrizeStatistics(scenes, pOrder);
        prizeStatistics.put("lastOrder", true);

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

    private List<Scene> getResultScene(double betScore, double factor, Player gamePlayer) {
        int betType = getBetType(gamePlayer);
        long now = TimeUtil.getNow();
        String pOrder = gamePlayer.getUser().getUserID() + "-" + now++;
        gamePlayer.getExtendJson().put("pOrder", pOrder);
        List<Scene> sceneIconVos = new ArrayList<>();
        Scene scene = generatedScene(betType, betScore, factor);
        scene.setOrder(nextId(now));
        scene.setPOrder(pOrder);
        List<Integer> scatterIndexes = checkScatterIndexes(scene);
        if (scatterIndexes.size() >= 3) {
            BonusData bonusData = generateBonusData(scatterIndexes, factor);
            PrizeIcon scatter = new PrizeIcon();
            scatter.setIcon(SCATTER);
            scatter.setGold(DecimalUtil.getBigDecimal2(betScore * bonusData.getTotal_multiplier()));
            scatter.setPrizeIndex(new HashSet<>(scatterIndexes));
            scene.getPrizeDetail().add(scatter);
            scene.setGold(DecimalUtil.getBigDecimal2(scene.getGold() + scatter.getGold().doubleValue()).doubleValue());
            scene.setBonusData(bonusData);
            scene.setBonusWin(scatter.getGold());
            scene.setType(1);
        }
        sceneIconVos.add(scene);
        return sceneIconVos;
    }

    private BonusData generateBonusData(List<Integer> scatterIndexes, double factor) {
        BonusData bonusData = new BonusData();
        int scatterBonusMul = scatterMulLineFactor(factor);
        bonusData.setScatters_multiplier(SCATTER_EXT_MUL[scatterIndexes.size() - 3]);
        bonusData.setScatters_count(scatterIndexes.size());
        bonusData.setBonus_multiplier(scatterBonusMul);
        bonusData.setTotal_multiplier(scatterBonusMul * bonusData.getScatters_multiplier());
        return bonusData;
    }

    public int scatterMulLineFactor(double factor) {
        factor = Math.max(0.6, Math.min(2, factor));
        double t = (factor - 0.6) / 1.4;
        double oneMinusT = 1.0 - t;
        double wa = oneMinusT * 0.9 + t * 0.6;
        double wb = oneMinusT * 0.08 + t * 0.15;

        double rand = RandomUtil.nextDouble();
        if (rand < wa) {
            return 1 + RandomUtil.nextInt(99);
        } else if (rand < wa + wb) {
            return RandomUtil.nextInt(60, 101);
        } else {
            return RandomUtil.nextInt(101, 999);
        }
    }

    private List<Integer> checkScatterIndexes(Scene scene) {
        List<Integer> scatterIndex = new ArrayList<>();
        int[][] rotary = scene.getRotary();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == SCATTER) {
                    scatterIndex.add(i * COLUMNS + i1);
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
                    if (i1 == 2) continue;

                    if (i1 == 4 && RandomUtil.nextDouble() <= 0.4) continue;
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
            if (i == 2) continue;

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
            if (RandomUtil.nextDouble() < 0.01 * factor) {
                scatterSize += 1;
            }
        }
        setScatterIcon(rotary, scatterSize);
        List<Integer> useIcons = arrToList();
        for (int i = 0; i < LotteryConfig.ROWS; i++) {
            if (rotary[i][2] == -1) {
                Map<Integer, Integer> prizeMaps = getPrize(rotary, i * LotteryConfig.COLUMNS + 2);
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
                        tempFactor *= 0.3780182;
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
        setMulWithScene(scene, betScore);
        return scene;
    }

    private static void setScatterIcon(int[][] rotary, int scatterSize) {
        if (scatterSize == 0) return;

        List<Integer> columns = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        for (int i = 0; i < scatterSize; i++) {
            Integer index = columns.remove(RandomUtil.nextInt(columns.size()));
            rotary[RandomUtil.nextInt(ROWS)][index] = LotteryConfig.SCATTER;
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
        if (icon != null && count >= 3 && icon != SCATTER) {
            Set<Integer> pos = new HashSet<>();
            for (int i = 0; i < count; i++) {
                pos.add(prizeLine[i]);
            }
            return new PrizeIcon(icon, hitLine, count, pos);
        }
        return null;
    }

    /**
     * 同一个图标保留最长连段
     */
    private static List<PrizeIcon> mergeMax(List<PrizeIcon> list) {
        Map<Integer, PrizeIcon> map = new HashMap<>();
        for (PrizeIcon r : list) {
            PrizeIcon old = map.get(r.getIcon());
            if (old == null || r.getLine() > old.getLine()) {
                map.put(r.getIcon(), r);
            }
        }
        return new ArrayList<>(map.values());
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
        for (Scene sceneIconVo : sceneIconVos) {
            totalWin += sceneIconVo.getGold();
        }

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
                return JSONObject.parseObject(gameDetail, RoundDetailDto.class);
            }
        } catch (Exception e) {
            log.error("rep record error", e);
        }
        return historyToClient;
    }
}
