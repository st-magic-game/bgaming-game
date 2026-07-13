package com.bgaming.aztecclusters.logic;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bgaming.aztecclusters.config.LotteryConfig;
import com.bgaming.aztecclusters.entity.PrizeIcon;
import com.bgaming.aztecclusters.entity.Scene;
import com.bgaming.aztecclusters.entity.WinResult;
import com.bgaming.aztecclusters.entity.dto.*;
import com.bgaming.aztecclusters.utils.DateTimeUtil;
import com.bgaming.aztecclusters.utils.FloodFillPrizeChecker;
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

import static com.bgaming.aztecclusters.config.LotteryConfig.*;
import static com.game.base.common.constant.GameKey.*;
import static com.game.base.common.constant.Protocol.*;

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
            int times = player.getETimes();
            int requestType = LotteryConfig.REQUEST_TYPE_NOR; // 0 普通  1免费  2普通*1.25  3购买
            double beforeScore = player.getUser().getScore();
            Double stake;
            List<Scene> scenes = getScenes(player);
            if (command.equals("freespin")) {
                if (scenes == null || scenes.isEmpty()) {
                    log.error("userId {} , error request freeSpin, scene == null", userId);
                    return null;
                }
                stake = scenes.get(0).getBetScoreServer() * LotteryConfig.SUB_UNITS;

                if (times >= scenes.size() || isErrorRequest(scenes)) {
                    log.error("userId {} , error request freeSpin, scenes size {} , times {}", userId, scenes.size(), times);
                    return null;
                }
                times++;
                SpinResponse response = getSpinResponse(player, 0, scenes, DecimalUtil.getBigDecimal2(stake / SUB_UNITS).doubleValue(), beforeScore, times);
                player.setETimes(times);
                log.info("玩家 {}  数据 result {}", player.getUserId(), response);
                return response;
            } else {
                stake = options.getDouble(BET);
                if (options.containsKey(LotteryConfig.PURCHASED_FEATURE)) {
                    String purchasedFeature = options.getString(LotteryConfig.PURCHASED_FEATURE);
                    if (purchasedFeature.equals(LotteryConfig.PURCHASED_FREE_SPIN_BUY)) {
                        String level = options.getString("purchased_feature_level");
                        requestType = Integer.parseInt(level) + 2;
                    }
                    if (purchasedFeature.equals(LotteryConfig.PURCHASED_FREE_SPIN_CHANCE)) {
                        requestType = REQUEST_TYPE_WILD_CHANCE;
                    }
                }
            }
            if (environmentCheck(player, userId)) return null;

            stake = DecimalUtil.getBigDecimal2(stake).doubleValue();
            if (cheatingDetection(player, stake, requestType)) return null;

            double orderStake = DecimalUtil.getBigDecimal2(stake * LotteryConfig.BET_TYPE_MUL[requestType] / LotteryConfig.SUB_UNITS).doubleValue();
            if (notEnoughGold(orderStake, beforeScore)) {
                log.info("玩家{} 余额不足,下注失败, score {} , betScore {} orderStake {}", player.getUser().getUserID(), beforeScore, stake, orderStake);
                return null;
            }

            if (!checkBetScore(player, stake)) {
                log.error("玩家{}下注分数异常, betScore {} , buyFree {} ", player.getUser().getUserID(), stake, orderStake);
                return null;
            }

            stake = DecimalUtil.getBigDecimal2(stake / LotteryConfig.SUB_UNITS).doubleValue();
            this.lastStartTime = TimeUtil.getNow();
            player.getExtendJson().put(BET_MUL, 1);
            player.getExtendJson().put(LotteryConfig.REQUEST_TYPE, requestType);
            double factor = GameContext.nextDouble(player, stake);
            double winGold = 0;
            JSONObject resultData;
            int recount = 0;
            do {
                this.totalWinGold = 0;
                if (recount++ > 3) {
                    factor = 0.02;
                }
                resultData = this.codeResultData(player, stake, factor);
                winGold = this.getWinGold();
                if (requestType > 0) {
                    player.getExtendJson().put(BUY_FREE, 1);
                }
                if (resultData == null) {
                    log.error("userId {} , orderStake {} ####### 重开", player.getUserId(), orderStake);
                }
            } while (resultData == null || (winGold - orderStake > 0 && reset(stake, winGold, player, 10, 300, 3, 100)));

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
            scene.setPOrder(pOrder);
            player.getExtendJson().put("pOrder", pOrder);
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
                List<RoundDetailDto> roundDetailDto = generateRoundDetail(tmpScene.getBeforeScore(), player, tmpScene);
                roundDetailDtos.addAll(roundDetailDto);
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

    private JSONObject getExtendData(Player player, Scene scene, boolean finish) {
        JSONObject extendData = getExtendString(player, scene.getPOrder(), finish);
        extendData.put(FREE_TYPE, scene.getFreeType());
        extendData.put(BUY_TYPE, scene.getFreeType());
        extendData.put(BET_TYPE, scene.getBetType());
        return extendData;
    }

    private static boolean checkFinishScene(Scene scene) {
        return (scene.getType() == 0 && scene.getOpenFreeNum() == 0)
                || (scene.getType() == 1 && scene.getFreeNum() == 1 && scene.getOpenFreeNum() == 0);
    }

    private static boolean isErrorRequest(List<Scene> scenes) {
        boolean errorRequest = false;
        Scene lastScene = scenes.get(scenes.size() - 1);
        if (lastScene.getType() == 0) {
            if (lastScene.getOpenFreeNum() == 0) {
                errorRequest = true;
            }
        } else {
            if (lastScene.getFreeNum() == 0 && lastScene.getOpenFreeNum() == 0) {
                errorRequest = true;
            }
        }
        return errorRequest;
    }

    private List<RoundDetailDto> generateRoundDetail(double beforeScore, Player player, Scene scene) {
        List<RoundDetailDto> roundDetailDtoList = new ArrayList<>();
        List<List<PrizeIcon>> prizeDetails = scene.getPrizeDetailList();
        List<List<Object>> features = scene.getStorage().getFeatures();
        boolean spin = true;
        for (int i = 0; i < scene.getStorage().getSaved_screens().size(); i++) {
            List<PrizeIcon> prizeDetail = new ArrayList<>();
            List<List<String>> rotary = scene.getStorage().getSaved_screens().get(i);
            int[][] multiBoard = scene.getStorage().getMultipliers_board().get(i);
            if (prizeDetails.size() > i) {
                prizeDetail = prizeDetails.get(i);
            }
            RoundDetailDto roundDetailDto = new RoundDetailDto();
            if (spin) {
                roundDetailDto.setSpin(spin);
                spin = false;
            }
            roundDetailDto.setTime(DateTimeUtil.parseDateTime(new Timestamp(TimeUtil.getNow()).toLocalDateTime()));
            roundDetailDto.setNumber(i + 1);
            roundDetailDto.setTotalFreeNum(scene.getTotalFreeNum());
            roundDetailDto.setFreeNum(scene.getTotalFreeNum() - scene.getFreeNum() + 1);
            roundDetailDto.setUsedFeature(scene.getBetType() == LotteryConfig.REQUEST_TYPE_FREE_1);
            roundDetailDto.setFeatureName(featureNameByBetType(scene.getBetType()));
            BigDecimal realBet = DecimalUtil.getBigDecimal2(scene.getBetScore());
            BigDecimal realWin = BigDecimal.ZERO;
            Optional<BigDecimal> reduce = prizeDetail.stream().map(PrizeIcon::getTotalGold).reduce(BigDecimal::add);
            if (reduce.isPresent()) {
                realWin = reduce.get();
            }
            BigDecimal realProfit = DecimalUtil.getBigDecimal2(scene.getGold() - scene.getBetScore());
            roundDetailDto.setTotalRoundWinText(DecimalUtil.getBigDecimal2(scene.getGold() / scene.getDoubleMul()).stripTrailingZeros().toPlainString());
            roundDetailDto.setBetText(betScoreText(scene));
            roundDetailDto.setOpenFreeNum(scene.getOpenFreeNum());
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
            roundDetailDto.setSymbols(castDetailSymbol(rotary, multiBoard));
            roundDetailDto.setFeatures(castFeatureStr(features, i, scene.getScatterIndexes()));
            roundDetailDtoList.add(roundDetailDto);
        }
        return roundDetailDtoList;
    }

    private List<String> castFeatureStr(List<List<Object>> features, int i, List<Integer> scatterIndexes) {
        List<String> featureTips = new ArrayList<>();
        if(features.isEmpty()) return featureTips;

        for (List<Object> feature : features) {
            Integer casedeId = (Integer) feature.get(feature.size() - 1);
            if (casedeId == i) {
                String featureDropName = feature.get(0) + "";
                String featureName = nameByDrop(featureDropName);
                if (featureName == null) continue;

                Object posi = feature.get(1);
                int[] position = new int[2];
                if(posi instanceof int[]){
                    int[] tmp = (int[]) posi;
                    position[0] = tmp[0];
                    position[1] = tmp[1];
                }else if (posi instanceof JSONArray){
                    JSONArray tmp = (JSONArray) posi;
                    Object[] array = tmp.toArray();
                    for (int i1 = 0; i1 < array.length; i1++) {
                        int p = Integer.parseInt(array[i] + "");
                        position[i1] = p;
                    }
                }
                if (scatterIndexes.contains(position[1] * COLUMNS + position[0])) continue;

                String strFormat = " Feature %s drops on [%s, %s] position ";
                String featureTip = String.format(strFormat, featureName, position[0], position[1]);
                featureTips.add(featureTip);
            }
        }
        return featureTips;
    }

    private String nameByDrop(String featureDropName) {
        switch (featureDropName) {
            case "wild_drops":
                return "Wild";
            case "multi_booster_drops":
                return "Multi Booster";
            case "destroyer_drops":
                return "Destroyer";
        }
        return null;
    }

    private String betScoreText(Scene scene) {
        if (scene.getType() == 1) return "0";

        if (scene.getBetType() == LotteryConfig.REQUEST_TYPE_NOR) {
            return DecimalUtil.getBigDecimal2(scene.getBetScore()).stripTrailingZeros().toPlainString();
        }

        return BET_TYPE_MUL[scene.getBetType()] + " * " + toPlainString(scene.getBetScoreServer()) + " = " + toPlainString(scene.getBetScore());
    }

    private String toPlainString(double betScore) {
        return DecimalUtil.getBigDecimal2(betScore).stripTrailingZeros().toPlainString();
    }

    private String featureNameByBetType(int betType) {
        if (betType == REQUEST_TYPE_WILD_CHANCE) return "Freespin chance";

        if (betType >= LotteryConfig.REQUEST_TYPE_FREE_0) return "Freespin buy";
        return "No";
    }

    private List<List<String>> castDetailWinLine(List<PrizeIcon> prizeDetail) {
        List<List<String>> result = new ArrayList<>();
        for (PrizeIcon prizeIcon : prizeDetail) {
            if (prizeIcon.getIcon() == LotteryConfig.SCATTER) continue;

            String line = String.valueOf(prizeIcon.getLine()).concat("x");
            String iconStr = SYMBOL_NAME[prizeIcon.getIcon()];
            String symbolWinInfoFormat = " pay as: %s * multiplier(%s) = %s ";
            String symbolWinInfo = String.format(symbolWinInfoFormat, prizeIcon.getGold().stripTrailingZeros().toPlainString(), prizeIcon.getExtMul(), prizeIcon.getTotalGold().stripTrailingZeros().toPlainString());
            List<String> winLine = Arrays.asList(line, iconStr, symbolWinInfo);
            result.add(winLine);
        }
        return result;
    }

    private List<SymbolInfo> castDetailSymbol(List<List<String>> rotary, int[][] multiBoard) {
        List<SymbolInfo> symbols = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                String iconStr = rotary.get(i1).get(i);
                int mulNum = multiBoard[i1][i];
                int icon = Integer.parseInt(iconStr);
                String symbolName = SYMBOL_NAME[icon];
                SymbolInfo symbolInfo = new SymbolInfo();
                symbolInfo.setName(symbolName);
                if (mulNum == 0) {
                    symbolInfo.setStyle("background-color: white"); //  normal
                } else if (mulNum == 1) {
                    symbolInfo.setStyle("background-color: #A8A8A8"); // 等级1
                    symbolInfo.setCenterValue(true);
                } else {
                    symbolInfo.setStyle("background-color: #585858"); // 等级>1
                    symbolInfo.setCenterValue(true);
                }
                if (mulNum >= 1) {
                    if (mulNum < 10) {
                        symbolInfo.setMarginLeft(85);
                    } else if (mulNum < 100) {
                        symbolInfo.setMarginLeft(75);
                    } else {
                        symbolInfo.setMarginLeft(65);
                    }
                    if (mulNum > 1) {
                        symbolInfo.setScore(String.valueOf(mulNum));
                    }
                }
                symbols.add(symbolInfo);
            }
        }
        return symbols;
    }

    private static final String[] SYMBOL_NAME = {"wild", "h1", "h2", "h3", "l1", "l2", "l3", "l4", "scatter"};

    private void resetPlayerExt(Player player) {
        player.getExtendJson().remove(LotteryConfig.REQUEST_TYPE);
        player.setETimes(0);
        player.getExtendJson().remove(BET_TYPE);
        player.getExtendJson().remove(BUY_FREE);
    }

    public SpinResponse generateResponse(List<Scene> scenes, boolean finish, double betAfterScore) {
        Scene scene = scenes.get(scenes.size() - 1);
        int requestType = scenes.get(0).getBetType();
        Double totalWin = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
        Optional<Double> freeTotalWinOptional = scenes.stream().filter(s -> s.getType() == 1).map(Scene::getGold).reduce(Double::sum);
        double freeTotalWin = 0;
        if (freeTotalWinOptional.isPresent()) {
            freeTotalWin = freeTotalWinOptional.get();
        }
        double betScore = scene.getBetScoreServer();
        String orderId = scene.getOrder();
        int orderSer = scene.getNumber() + 1;
        BgBalance balance = new BgBalance();
        balance.setGame(DecimalUtil.getBigDecimal2(totalWin * LotteryConfig.SUB_UNITS));
        balance.setWallet(DecimalUtil.getBigDecimal2(betAfterScore * LotteryConfig.SUB_UNITS));

        FlowData flowData = this.table.getGameService().initFlowData();
        List<String> actions = new ArrayList<>();
        actions.add(BGAMING_COMMAND_INIT);
        if (scene.getOpenFreeNum() > 0) {
            flowData.setState(LotteryConfig.BGAMING_COMMAND_FREE_SPIN);
            actions.add("freespin");
        } else {
            if (scene.getType() == 1) {
                if (scene.getFreeNum() > 1) {
                    actions.add("freespin");
                    flowData.setState(finish ? BGAMING_STATE_CLOSED : BGAMING_COMMAND_FREE_SPIN);
                } else {
                    actions.add(BGAMING_COMMAND_SPIN);
                    flowData.setState(BGAMING_STATE_CLOSED);
                }
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
        JSONObject purchasedFeature = new JSONObject();
        if (requestType > LotteryConfig.REQUEST_TYPE_WILD_CHANCE) {
            purchasedFeature.put("name", LotteryConfig.PURCHASED_FREE_SPIN_BUY);
            purchasedFeature.put("level", requestType - 2);
        } else if (requestType == REQUEST_TYPE_WILD_CHANCE) {
            purchasedFeature.put("name", LotteryConfig.PURCHASED_FREE_SPIN_CHANCE);
        }
        jsonObject.put(LotteryConfig.PURCHASED_FEATURE, purchasedFeature);

        int freeSpinIssued = checkIssued(scene);
        int freeSpinLeft = checkFreeSpinLeft(scene);
        GameFeatures gameFeatures = new GameFeatures();
        gameFeatures.setFreespins_issued(freeSpinIssued);
        gameFeatures.setFreespins_left(freeSpinLeft);
        gameFeatures.setTotal_fs_win(DecimalUtil.getBigDecimal2(freeTotalWin));

        OutCome outCome = new OutCome();
        outCome.setBet(DecimalUtil.getBigDecimal2(betScore * LotteryConfig.SUB_UNITS));
        outCome.setWin(DecimalUtil.getBigDecimal2(scene.getGold() * LotteryConfig.SUB_UNITS));
        outCome.setSpecial_symbols(scene.getSpecial_symbols());
        outCome.setWins(checkWins(scene.getPrizeDetailList()));
        outCome.setScreen(castColumnToRow(scene.getRotary()));
        outCome.setFreespins_issued(scene.getOpenFreeNum());
        outCome.setStorage(scene.getStorage());

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

    private List<List<Object>> checkWins(List<List<PrizeIcon>> prizeDetailList) {
        List<List<Object>> result = new ArrayList<>();
        for (int i = 0; i < prizeDetailList.size(); i++) {
            List<PrizeIcon> prizeDetail = prizeDetailList.get(i);
            for (PrizeIcon prizeIcon : prizeDetail) {
                List<Object> win = new ArrayList<>();
                if (prizeIcon.getIcon() == LotteryConfig.SCATTER) {
                    Set<Integer> prizeIndex = prizeIcon.getPrizeIndex();
                    List<List<Integer>> scatterIndex = new ArrayList<>();
                    for (Integer index : prizeIndex) {
                        List<Integer> idx = new ArrayList<>();
                        idx.add(index % COLUMNS);
                        idx.add(index / COLUMNS);
                        scatterIndex.add(idx);
                    }
                    win.add("scatter");
                    win.add(0);
                    win.add(scatterIndex);
                } else {
                    win.add("cascade_" + i);
                    win.add(prizeIcon.getTotalGold().multiply(BigDecimal.valueOf(LotteryConfig.SUB_UNITS)));
                    win.add(castColumnIdx(prizeIcon.getPrizeIndex()));
                    win.add(String.valueOf(prizeIcon.getIcon()));
                    win.add(prizeIcon.getExtMul());
                }
                result.add(win);
            }
        }
        return result;
    }

    private List<List<Integer>> castColumnIdx(Set<Integer> prizeIndex) {
        List<List<Integer>> result = new ArrayList<>();
        for (Integer idx : prizeIndex) {
            List<Integer> index = new ArrayList<>();
            index.add(idx % COLUMNS);
            index.add(idx / COLUMNS);
            result.add(index);
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

    private int getRequestType(Player player) {
        int betType = 0;
        if (player.getExtendJson().containsKey(LotteryConfig.REQUEST_TYPE)) {
            betType = player.getExtendJson().getInteger(LotteryConfig.REQUEST_TYPE);
        }
        return betType;
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

    private static boolean cheatingDetection(Player player, Double stake, int requestType) {
        if (stake < 0 || requestType < 0 || requestType > 5) {
            log.error("user {} , 作弊检测篡改数据!!! betScore {} requestType {}", player.getUser().getUserID(), stake, requestType);
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
    private JSONObject getExtendString(Player player, String pOrder, boolean lastOrder) {
        List<Scene> scenes = getScenes(player);
        if (scenes == null) {
            log.error("发送注单时，服务器发生错误");
            throw new RuntimeException("发送注单数据错误");
        }

        JSONObject prizeStatistics = this.getPrizeStatistics(scenes, pOrder);
        prizeStatistics.put("lastOrder", lastOrder);

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

    private List<Scene> getResultScene(double betScore, double factor, Player player) {
        List<Scene> result = new ArrayList<>();
        int betType = getRequestType(player);
        long now = TimeUtil.getNow();
        int type = 0;
        int freeNum = 0;
        int number = 0;
        int totalFreeNum = 0;
        int[][] multiplierBoard = new int[ROWS][COLUMNS];
        List<Integer> holdWildIndexes = new ArrayList<>();
        do {
            Scene sceneIconVo = generatedScene(betScore, betType, factor, type, multiplierBoard, holdWildIndexes);
            if (sceneIconVo == null) {
                return null;
            }

            checkAndSetFreeInfo(sceneIconVo);
            setSpecialSymbols(sceneIconVo);
            totalFreeNum += sceneIconVo.getOpenFreeNum();
            sceneIconVo.setOrder(nextId(now));
            sceneIconVo.setFreeNum(freeNum);
            sceneIconVo.setNumber(number++);
            sceneIconVo.setTotalFreeNum(totalFreeNum);
            sceneIconVo.setBetType(betType);
            if (sceneIconVo.getOpenFreeNum() > 0) {
                if (type == 0) {
                    holdWildIndexes.clear();
                    multiplierBoard = new int[ROWS][COLUMNS];
                }
                setScatterFeatures(sceneIconVo, multiplierBoard, factor, betType, holdWildIndexes);
                freeNum += sceneIconVo.getOpenFreeNum();
                type = 1;
            }
            if (sceneIconVo.getType() == 1) {
                freeNum--;
            }
            betType = 0;
            result.add(sceneIconVo);
        } while (freeNum > 0);

        return result;
    }

    private static void setWildFeatures(Scene sceneIconVo, int[][] multiplierBoard) {
        List<Integer> newWildIndexes = sceneIconVo.getNewWildIndexes();
        for (Integer index : newWildIndexes) {
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            int mul = 1;
            if (multiplierBoard[y][x] > 0) {
                multiplierBoard[y][x] = 10;
                mul = 10;
            } else {
                multiplierBoard[y][x] = 1;
            }
            sceneIconVo.getStorage().getFeatures().add(Arrays.asList("wild_drops", new int[]{x, y}, mul, 0));
        }
    }

    private void setScatterFeatures(Scene sceneIconVo, int[][] multiplierBoard, double factor, int betType, List<Integer> holdWildIndexes) {
        int wildSize = 0;
        switch (betType) {
            case REQUEST_TYPE_FREE_0:
                if (RandomUtil.nextDouble() < factor * 0.05) {
                    wildSize = 1;
                }
                break;
            case REQUEST_TYPE_FREE_1:
                wildSize = 1;
                if (RandomUtil.nextDouble() < factor * 0.05) {
                    wildSize = 2;
                }
                break;
            case REQUEST_TYPE_FREE_2:
                wildSize = 2;
                if (RandomUtil.nextDouble() < factor * 0.05) {
                    wildSize = 3;
                }
                break;
            case REQUEST_TYPE_FREE_3:
                wildSize = 3;
                break;
            default:
                if (RandomUtil.nextDouble() < factor * 0.03) {
                    wildSize = 1;
                }
        }
        List<Integer> iconIndexes = getIconIndexes(sceneIconVo.getFinalRotary(), SCATTER);
        sceneIconVo.setScatterIndexes(new ArrayList<>(iconIndexes));
        Collections.shuffle(iconIndexes);
        int cascadeIndex = sceneIconVo.getStorage().getSaved_screens().size() - 1;
        for (int i = 0; i < wildSize; i++) {
            Integer index = iconIndexes.remove(0);
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            multiplierBoard[y][x] = 1;
            sceneIconVo.getStorage().getFeatures().add(Arrays.asList("wild_drops", new int[]{x, y}, 1, cascadeIndex));
            holdWildIndexes.add(index);
        }

        for (Integer index : iconIndexes) {
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            multiplierBoard[y][x] = 10;
            sceneIconVo.getStorage().getFeatures().add(Arrays.asList("multiplier_drops", new int[]{x, y}, 10, cascadeIndex));
        }
    }

    private void setSpecialSymbols(Scene sceneIconVo) {
        int[][] finalRotary = sceneIconVo.getFinalRotary();
        List<int[]> wildIndex = new ArrayList<>();
        List<int[]> scatterIndex = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (finalRotary[i][i1] == WILD) {
                    wildIndex.add(new int[]{i1, i});
                }

                if (finalRotary[i][i1] == SCATTER) {
                    scatterIndex.add(new int[]{i1, i});
                }
            }
        }
        if (!wildIndex.isEmpty()) {
            Map<Integer, Object> inner = new HashMap<>();
            inner.put(WILD, wildIndex);
            sceneIconVo.getSpecial_symbols().put("wild", inner);
        }
        if (!scatterIndex.isEmpty()) {
            Map<Integer, Object> inner = new HashMap<>();
            inner.put(SCATTER, scatterIndex);
            sceneIconVo.getSpecial_symbols().put("scatter", inner);
        }
    }

    private void checkAndSetFreeInfo(Scene sceneIconVo) {
        int[][] rotary = sceneIconVo.getFinalRotary();
        List<Integer> scatterIndexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == LotteryConfig.SCATTER) {
                    scatterIndexes.add(i * COLUMNS + i1);
                }
            }
        }
        if (scatterIndexes.size() >= 3) {
            int openFreeNum = LotteryConfig.FREE_NUM[Math.min(scatterIndexes.size() - 3, 3)];
            sceneIconVo.setOpenFreeNum(openFreeNum);
        }
    }

    private static int[][] generateNoPrizeScene() {
        int[][] scene = new int[ROWS][COLUMNS];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                int icon;
                while (true) {
                    icon = LotteryConfig.getRandomNormalIcon(1);
                    boolean ok = true;
                    // 左边
                    if (x > 0 && scene[y][x - 1] == icon) {
                        ok = false;
                    }
                    // 上面
                    if (y > 0 && scene[y - 1][x] == icon) {
                        ok = false;
                    }
                    if (ok) break;
                }
                scene[y][x] = icon;
            }
        }
        return scene;
    }

    private static Scene generatedScene(double betScore, int betType, double factor, int type, int[][] multiplierBoard, List<Integer> holdWildIndexes) {
        if (type == 1) {
            factor *= 1.58568;
        }
        Scene scene = new Scene();
        scene.setType(type);
        scene.setDoubleMul(1);
        int[][] rotary = buildFirstScreen(scene, multiplierBoard, factor, betType, holdWildIndexes);
        scene.setRotary(copyArr(rotary));
        scene.setFreeType(betType);
        setWildFeatures(scene, multiplierBoard);
        int cascadeIndex = 0;
        double totalWin = 0;
        factor *= BET_TYPE_FACTOR[betType];
        while (true) {
            scene.getStorage().getMultipliers_board().add(copyMultiArr(multiplierBoard));
            scene.getStorage().getSaved_screens().add(castColumnToRow(rotary));

            WinResult winResult = calculateWin(rotary, multiplierBoard, betScore);
            increamentMulBoard(winResult, rotary, multiplierBoard);
            if(!winResult.getPrizeIcons().isEmpty()){
                scene.getPrizeDetailList().add(winResult.getPrizeIcons());
            }
            totalWin += winResult.getWinAmount();
            if (winResult.getPrizeIndex().isEmpty()) break;

            rotary = buildNextScreen(rotary, multiplierBoard, new HashSet<>(winResult.getPrizeIndex()), factor, scene, cascadeIndex++, holdWildIndexes);
            if (scene.getStorage().getSaved_screens().size() > 15) {
                return null;
            }
        }
        scene.setFinalRotary(rotary);
        scene.setGold(DecimalUtil.getBigDecimal2(totalWin).doubleValue());
        return scene;
    }

    private static void destroyAllLowSymbols(int[][] rotary, Set<Integer> prizeIndex) {
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] >= 4 && rotary[i][i1] <= 7) {
                    prizeIndex.add(i * COLUMNS + i1);
                }
            }
        }
    }

    private static void multiBoosterBoard(int[][] rotary, int[][] multiplierBoard) {
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                int mul = multiplierBoard[i][i1];

                int newMul;
                if (rotary[i][i1] == WILD) {
                    if (mul == 1) {
                        mul = 0;
                    }
                    newMul = mul + 10;
                    multiplierBoard[i][i1] = Math.min(newMul, 100);
                } else {
                    if (mul > 1) {
                        newMul = mul + 2;
                        multiplierBoard[i][i1] = Math.min(newMul, 10);
                    }
                }
            }
        }
    }

    private static void increamentMulBoard(WinResult winResult, int[][] rotary, int[][] multiplierBoard) {
        Set<Integer> prizeIndex = winResult.getPrizeIndex();
        for (Integer index : prizeIndex) {
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            incrementMul(rotary, multiplierBoard, y, x);
        }
    }

    private static void incrementMul(int[][] rotary, int[][] multiplierBoard, int y, int x) {
        int mul = multiplierBoard[y][x];
        int newMul;
        if (rotary[y][x] == WILD) {
            if (mul == 0) {
                newMul = 10;
            } else if (mul < 10) {
                newMul = 10;
            } else {
                newMul = mul + 10;
            }
            multiplierBoard[y][x] = Math.min(newMul, 100);
        } else {
            if (mul == 0) {
                newMul = 1;
            } else if (mul == 1) {
                newMul = 2;
            } else {
                newMul = mul + 2;
            }
            multiplierBoard[y][x] = Math.min(newMul, 10);
        }
    }

    private static int[][] buildFirstScreen(Scene scene, int[][] multiplierBoard, double factor, int betType, List<Integer> holdWildIndexes) {
        int[][] rotary = generatedRotary(betType, factor);
        for (Integer wildIndex : holdWildIndexes) {
            rotary[wildIndex / COLUMNS][wildIndex % COLUMNS] = WILD;
        }
        int scatterSize = LotteryConfig.getScatterSize(1);
        if (betType >= LotteryConfig.REQUEST_TYPE_FREE_0) {
            scatterSize = 3;
            if (RandomUtil.nextDouble() < 0.08) {
                scatterSize = 4;
            }
            installScatterAnywhere(rotary, scatterSize);
        } else {
            installScatter(rotary, scatterSize);
        }

        int wildSize = LotteryConfig.getWildSize(betType, factor > 1 ? 1 : factor);
        if (holdWildIndexes.size() > 2 || scene.getType() == 1) {
            wildSize = 0;
        }
        List<Integer> newWildIndexes;
        if (betType == REQUEST_TYPE_WILD_CHANCE) {
            newWildIndexes = installWildAnywhere(rotary, wildSize);
        } else {
            newWildIndexes = installWild(rotary, wildSize);
        }
        scene.setNewWildIndexes(new ArrayList<>(newWildIndexes));
        newWildIndexes.removeAll(holdWildIndexes);
        holdWildIndexes.addAll(newWildIndexes);

        factor *= BET_TYPE_FACTOR[betType];
        fillScreen(rotary, multiplierBoard, factor, false);
        return rotary;
    }

    private static int[][] generatedRotary(int betType, double factor) {
        int[][] rotary;
        if (betType >= REQUEST_TYPE_FREE_0 || RandomUtil.nextDouble() * factor < NO_WIN_PRO) {
            rotary = generateNoPrizeScene();
        } else {
            rotary = getInitRotary();
        }
        return rotary;
    }

    private static List<Integer> installWildAnywhere(int[][] rotary, int wildSize) {
        List<Integer> newWildIndexes = new ArrayList<>();
        if (wildSize <= 0) {
            return newWildIndexes;
        }
        List<Integer> columns = new ArrayList<>(
                Arrays.asList(0, 1, 2, 3, 4, 5)
        );
        int retry = 0;
        while (!columns.isEmpty() && newWildIndexes.size() < wildSize) {
            Integer column = columns.remove(RandomUtil.nextInt(columns.size()));
            List<Integer> rows = new ArrayList<>();
            for (int y = 0; y < ROWS; y++) {
                if (canInstallWild(rotary, y, column)) {
                    rows.add(y);
                }
            }

            if (rows.isEmpty()) {
                continue;
            }
            int y = rows.get(RandomUtil.nextInt(rows.size()));
            rotary[y][column] = WILD;
            newWildIndexes.add(y * COLUMNS + column);
            retry++;
            if (retry > 10) {
                break;
            }
        }
        return newWildIndexes;
    }

    private static List<Integer> installWildAnywhere1(int[][] rotary, int wildSize) {
        List<Integer> newWildIndexes = new ArrayList<>();
        if (wildSize == 0) return newWildIndexes;

        List<Integer> canUseColumnIds = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5));
        for (int i = 0; i < wildSize; i++) {
            Integer index = canUseColumnIds.remove(RandomUtil.nextInt(canUseColumnIds.size()));
            int y = RandomUtil.nextInt(ROWS);
            rotary[y][index] = WILD;
            newWildIndexes.add(y * COLUMNS + index);
        }
        return newWildIndexes;
    }

    private static List<Integer> installWild(int[][] rotary, int wildSize) {
        List<Integer> newWildIndexes = new ArrayList<>();
        if (wildSize <= 0) {
            return newWildIndexes;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                if (rotary[y][x] == WILD) {
                    continue;
                }
                if (canInstallWild(rotary, y, x)) {
                    candidates.add(y * COLUMNS + x);
                }
            }
        }
        while (!candidates.isEmpty() && newWildIndexes.size() < wildSize) {
            int removeIndex = RandomUtil.nextInt(candidates.size());
            int index = candidates.remove(removeIndex);
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            rotary[y][x] = WILD;
            newWildIndexes.add(index);
            for (int i = candidates.size() - 1; i >= 0; i--) {
                int next = candidates.get(i);
                if (!canInstallWild(rotary, next / COLUMNS, next % COLUMNS)) {
                    candidates.remove(i);
                }
            }
        }
        return newWildIndexes;
    }

    private static boolean canInstallWild(int[][] rotary, int y, int x) {
        // 左
        if (x > 0 && rotary[y][x - 1] == WILD) {
            return false;
        }
        // 右
        if (x < COLUMNS - 1 && rotary[y][x + 1] == WILD) {
            return false;
        }
        // 上
        if (y > 0 && rotary[y - 1][x] == WILD) {
            return false;
        }
        // 下
        if (y < ROWS - 1 && rotary[y + 1][x] == WILD) {
            return false;
        }
        return true;
    }


    private static boolean canInstallDropWild(int[][] board, int y, int x) {
        if (board[y][x] == WILD) {
            return false;
        }
        return canInstallWild(board, y, x);
    }

    public static void main(String[] args) {
        int[][] scene = {
                {2, 7, 7, 5, 7, 7},
                {5, 7, 7, 7, 2, 2},
                {1, 6, 7, 1, 7, 7},
                {1, 7, 2, 5, 7, 2},
                {5, 2, 7, 1, 6, 6},
                {1, 3, 2, 3, 3, 3},
                {6, 3, 3, 2, 5, 7},
                {2, 1, 6, 6, 4, 1},
        };
        WinResult winResult = calculateWin(scene, new int[ROWS][COLUMNS], 1);


    }

    private static WinResult calculateWin(int[][] rotary, int[][] multiplierBoard, double betScore) {
        List<PrizeIcon> prizeIcons = FloodFillPrizeChecker.checkPrizeIcon(rotary, betScore);

        Set<Integer> prizeIndex = new HashSet<>();
        double win = 0;
        for (PrizeIcon prizeIcon : prizeIcons) {
            int extMul = sumBoardMul(prizeIcon.getPrizeIndex(), multiplierBoard);
            extMul = Math.max(extMul, 1);
            prizeIcon.setExtMul(extMul);
            prizeIcon.setTotalGold(DecimalUtil.getBigDecimal2(extMul * prizeIcon.getGold().doubleValue()));
            prizeIndex.addAll(prizeIcon.getPrizeIndex());
            win += prizeIcon.getTotalGold().doubleValue();
        }
        return new WinResult(prizeIcons, prizeIndex, win);
    }

    private static int sumBoardMul(Set<Integer> prizeIndex, int[][] multiplierBoard) {
        int extMul = 0;
        for (Integer index : prizeIndex) {
            int x = index % COLUMNS;
            int y = index / COLUMNS;
            int boardMul = multiplierBoard[y][x];
            if (boardMul > 1) {
                extMul += boardMul;
            }
        }
        return extMul;
    }

    private static int[][] buildNextScreen(int[][] rotary, int[][] multiplierBoard, Set<Integer> prizeIndex, double factor, Scene scene, int cascadeIndex, List<Integer> holdWildIndexes) {
        rotary = installDropWild(multiplierBoard, rotary, scene, prizeIndex, holdWildIndexes, cascadeIndex);
        installChangeScatter(rotary, scene, prizeIndex, cascadeIndex);

        boolean destroy = installDropDestroy(rotary, prizeIndex, scene, cascadeIndex);
        scene.setDestroy(destroy);
        int[][] next = dropRotaryWithoutWild(prizeIndex, rotary, holdWildIndexes);
        if (!destroy && cascadeIndex > 0) {
            boolean multiBooster = installMultiIcon(next, prizeIndex, scene, cascadeIndex);
            scene.setMultiBooster(multiBooster);
            if (multiBooster) {
                multiBoosterBoard(rotary, multiplierBoard);
            }
        }
        int iconSize = getIconSize(next, SCATTER);
        if (iconSize < 4) {
            installDropScatter(next);
        }
        fillScreen(next, multiplierBoard, factor, true);
        return next;
    }

    private static void installDropScatter(int[][] next) {
        int dropScatterSize = getDropWildSize();
        Map<Integer, List<Integer>> canInstallIndexesMap = getCanInstallIndexesMap(next);
        int size = Math.min(dropScatterSize, canInstallIndexesMap.keySet().size());
        for (int i = 0; i < size; i++) {
            ArrayList<Integer> keys = new ArrayList<>(canInstallIndexesMap.keySet());
            Collections.shuffle(keys);
            Integer index = keys.remove(0);
            List<Integer> indexes = canInstallIndexesMap.get(index);
            Integer idx = indexes.get(RandomUtil.nextInt(indexes.size()));
            next[idx / COLUMNS][idx % COLUMNS] = LotteryConfig.SCATTER;
        }
    }

    private static boolean installMultiIcon(int[][] next, Set<Integer> prizeIndex, Scene scene, int cascadeIndex) {
        if (RandomUtil.nextDouble() < MULTI_PRO) {
            List<Integer> canUseIndexes = checkNotWildIndex(next, prizeIndex);
            Collections.shuffle(canUseIndexes);
            int idx = canUseIndexes.remove(0);
            int[] position = {idx % COLUMNS, idx / COLUMNS};
            scene.getStorage().getFeatures().add(Arrays.asList("multi_booster_drops", position, cascadeIndex));
            return true;
        }
        return false;
    }

    private static List<Integer> checkNotWildIndex(int[][] next, Set<Integer> prizeIndex) {
        List<Integer> reuslt = new ArrayList<>();
        for (Integer index : prizeIndex) {
            if (next[index / COLUMNS][index % COLUMNS] != WILD) {
                reuslt.add(index);
            }
        }
        return reuslt;
    }

    private static boolean installDropDestroy(int[][] rotary, Set<Integer> prizeIndex, Scene scene, int cascadeIndex) {
        if (RandomUtil.nextDouble() < DESTROY_PRO) {
            List<Integer> canUseIndexes = checkNotWildIndex(rotary, prizeIndex);
            if (canUseIndexes.isEmpty()) return false;

            Collections.shuffle(canUseIndexes);
            int idx = canUseIndexes.remove(0);
            prizeIndex.remove((Integer) idx);
            int[] position = {idx % COLUMNS, idx / COLUMNS};
            scene.getStorage().getFeatures().add(Arrays.asList("destroyer_drops", position, cascadeIndex));
            destroyAllLowSymbols(rotary, prizeIndex);
            return true;
        }
        return false;
    }

    private static int[][] installDropWild(int[][] multiplierBoard, int[][] rotary, Scene scene, Set<Integer> prizeIndex, List<Integer> holdWildIndexes, int cascadeIndex) {
        if (prizeIndex.isEmpty()) {
            return rotary;
        }
        int[][] next = copyArr(rotary);
        int wildSize = getIconSize(next, WILD);
        if (wildSize >= 2) {
            return next;
        }
        int dropWildSize = getDropWildSize();
        if (dropWildSize + wildSize > 2) {
            dropWildSize = 2 - wildSize;
        }
        if (dropWildSize <= 0) {
            return next;
        }

        List<Integer> canUseIndexes = new ArrayList<>();
        for (Integer idx : prizeIndex) {
            int y = idx / COLUMNS;
            int x = idx % COLUMNS;
            if (canInstallDropWild(next, y, x)) {
                canUseIndexes.add(idx);
            }
        }
        if (canUseIndexes.isEmpty()) {
            return next;
        }
        int size = Math.min(dropWildSize, canUseIndexes.size());
        for (int i = 0; i < size; i++) {
            if (canUseIndexes.isEmpty()) {
                break;
            }
            int randomIndex = RandomUtil.nextInt(canUseIndexes.size());
            int idx = canUseIndexes.remove(randomIndex);
            int y = idx / COLUMNS;
            int x = idx % COLUMNS;
            next[y][x] = WILD;
            holdWildIndexes.add(idx);
            prizeIndex.remove(idx);
            int mul = 1;
            if (multiplierBoard[y][x] > 0) {
                multiplierBoard[y][x] = 10;
                mul = 10;
            }
            scene.getStorage().getFeatures().add(Arrays.asList("wild_drops", new int[]{x, y}, mul, cascadeIndex));
            for (int j = canUseIndexes.size() - 1; j >= 0; j--) {
                int nextIdx = canUseIndexes.get(j);
                int ny = nextIdx / COLUMNS;
                int nx = nextIdx % COLUMNS;
                if (!canInstallDropWild(next, ny, nx)) {
                    canUseIndexes.remove(j);
                }
            }
        }
        return next;
    }


    private static int getIconSize(int[][] rotary, int icon) {
        List<Integer> iconIndexes = getIconIndexes(rotary, icon);
        return iconIndexes.size();
    }

    private static List<Integer> getIconIndexes(int[][] rotary, int icon) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] == icon) {
                    indexes.add(i * COLUMNS + i1);
                }
            }
        }
        return indexes;
    }


    private static int[][] dropRotaryWithoutWild(Set<Integer> prizeIndex, int[][] rotary, List<Integer> holdWildIndexes) {
        int[][] dropRotary = getInitRotary();
        boolean[] holdWild = new boolean[ROWS * COLUMNS];
        for (Integer index : holdWildIndexes) {
            holdWild[index] = true;
        }
        for (int col = 0; col < COLUMNS; col++) {
            int[] temp = new int[ROWS];
            int size = 0;
            for (int row = ROWS - 1; row >= 0; row--) {
                int index = row * COLUMNS + col;
                // Wild固定，不参与移动
                if (holdWild[index]) {
                    continue;
                }
                // 消除
                if (prizeIndex.contains(index)) {
                    continue;
                }
                temp[size++] = rotary[row][col];
            }
            int idx = 0;
            for (int row = ROWS - 1; row >= 0; row--) {
                int index = row * COLUMNS + col;
                // Wild恢复原位置
                if (holdWild[index]) {
                    dropRotary[row][col] = rotary[row][col];
                    continue;
                }

                if (idx < size) {
                    dropRotary[row][col] = temp[idx++];
                }
            }
        }
        return dropRotary;
    }

    private static void fillScreen(int[][] rotary, int[][] multiplierBoard, double factor, boolean drop) {
        if (!drop) {
            installLongLine(rotary, factor);
        }
        installRandomIcon(factor, rotary, multiplierBoard);
    }

    private static void installLongLine(int[][] rotary, double factor) {
        List<Integer> canUseIndexes = getCanUseIndexes(rotary);
        if (!canUseIndexes.isEmpty()) {
            boolean winLongLine = RandomUtil.nextDouble() < LONG_LINE_WIN_PRO * factor;
            if (winLongLine) {
                int icon = getRandomNormalIcon(factor * 0.5);
                int lineLen = getRandomNormalIcon(factor) + 6;
                List<int[]> lineIndex = FloodFillPrizeChecker.pick(rotary, lineLen);
                for (int[] index : lineIndex) {
                    int y = index[0];
                    int x = index[1];
                    rotary[y][x] = icon;
                }
            }
        }
    }

    private static int checkConnectCount(int[][] rotary, List<int[]> positions, int x, int y, int icon) {
        boolean[][] visited = new boolean[ROWS][COLUMNS];
        int[] queue = new int[ROWS * COLUMNS];
        int head = 0;
        int tail = 0;
        queue[tail++] = y * COLUMNS + x;
        visited[y][x] = true;
        int count = 0;
        while (head < tail) {
            int index = queue[head++];
            int cx = index % COLUMNS;
            int cy = index / COLUMNS;
            positions.add(new int[]{cy, cx});
            count++;
            // 左
            if (cx > 0) {
                if (checkNext(rotary, visited, cx - 1, cy, icon)) {
                    queue[tail++] = cy * COLUMNS + cx - 1;
                    visited[cy][cx - 1] = true;
                }
            }
            // 右
            if (cx < COLUMNS - 1) {
                if (checkNext(rotary, visited, cx + 1, cy, icon)) {
                    queue[tail++] = cy * COLUMNS + cx + 1;
                    visited[cy][cx + 1] = true;
                }
            }
            // 上
            if (cy > 0) {
                if (checkNext(rotary, visited, cx, cy - 1, icon)) {
                    queue[tail++] = (cy - 1) * COLUMNS + cx;
                    visited[cy - 1][cx] = true;
                }
            }
            // 下
            if (cy < ROWS - 1) {
                if (checkNext(rotary, visited, cx, cy + 1, icon)) {
                    queue[tail++] = (cy + 1) * COLUMNS + cx;
                    visited[cy + 1][cx] = true;
                }
            }
        }
        return count;
    }

    private static boolean checkNext(int[][] rotary, boolean[][] visited, int x, int y, int icon) {
        if (visited[y][x]) {
            return false;
        }
        int value = rotary[y][x];
        return value == icon || value == WILD;
    }

    private static void installRandomIcon(double factor, int[][] rotary, int[][] multiplierBoard) {
        for (int x = 0; x < COLUMNS; x++) {
            for (int y = 0; y < ROWS; y++) {
                if (rotary[y][x] != -1) {
                    continue;
                }
                int icon = randomIcon(factor, rotary, multiplierBoard, x, y);
                rotary[y][x] = icon;
            }
        }
    }

    private static int randomIcon(double factor, int[][] rotary, int[][] multiplierBoard, int x, int y) {
        final int MAX_TRY = 30;
        for (int i = 0; i < MAX_TRY; i++) {
            int icon = LotteryConfig.getRandomNormalIcon(factor);
            rotary[y][x] = icon;
            List<int[]> positions = new ArrayList<>();
            int count = checkConnectCount(rotary, positions, x, y, icon);
            if (count < 5) {
                return icon;
            }

            int mul = LotteryConfig.getMul(icon, count);
            int extMul = checkExtMul(multiplierBoard, positions);
            double rate = Math.min(1d, factor / ((mul * extMul * 1.0d) / 100));
            if (RandomUtil.nextDouble() < rate) {
                return icon;
            }
        }

        /*
         * 30次都不接受
         * 生成安全图标
         */
        return getSafeIcon(rotary, x, y);
    }

    private static int checkExtMul(int[][] multiplierBoard, List<int[]> positions) {
        int extMul = 0;
        for (int[] position : positions) {
            int y = position[0];
            int x = position[1];
            int mul = multiplierBoard[y][x];
            if (mul > 1) {
                extMul += mul;
            }
        }
        return Math.max(extMul, 1);
    }

    private static int getSafeIcon(int[][] rotary, int x, int y) {
        for (int i = 0; i < 20; i++) {
            int icon = LotteryConfig.getRandomNormalIcon(1);
            rotary[y][x] = icon;
            if (checkConnectCount(rotary, new ArrayList<>(), x, y, icon) < 5) {
                return icon;
            }
        }
        // 极端情况
        return 7;
    }


    private static void installChangeScatter(int[][] dropRotary, Scene scene, Set<Integer> prizeIndex, int cascadeIndex) {
        if (RandomUtil.nextDouble() < DROP_SCATTER_PRO) {
            List<Integer> canUseIndexes = checkRowHasScatter(dropRotary, prizeIndex);
            if (canUseIndexes.isEmpty()) return;

            canUseIndexes = checkNotWildIndex(dropRotary, new HashSet<>(canUseIndexes));
            if (canUseIndexes.isEmpty()) return;

            Collections.shuffle(canUseIndexes);
            int idx = canUseIndexes.remove(0);
            int[] position = {idx % COLUMNS, idx / COLUMNS};
            dropRotary[idx / COLUMNS][idx % COLUMNS] = LotteryConfig.SCATTER;
            scene.getStorage().getFeatures().add(Arrays.asList("scatter_drops", position, cascadeIndex));
            prizeIndex.remove((Integer) idx);
        }
    }

    private static List<Integer> checkRowHasScatter(int[][] dropRotary, Set<Integer> prizeIndex) {
        List<Integer> canUseIndexes = new ArrayList<>();
        Map<Integer, List<Integer>> columnIds = new HashMap<>();
        for (Integer index : prizeIndex) {
            int columnId = index % COLUMNS;
            List<Integer> orDefault = columnIds.getOrDefault(columnId, new ArrayList<>());
            orDefault.add(index);
            columnIds.put(columnId, orDefault);
        }
        for (Integer columnId : columnIds.keySet()) {
            boolean hasScatter = false;
            for (int i = 0; i < ROWS; i++) {
                if (dropRotary[i][columnId] == SCATTER) {
                    hasScatter = true;
                    break;
                }
            }
            if (!hasScatter) {
                canUseIndexes.addAll(columnIds.get(columnId));
            }
        }

        return canUseIndexes;
    }

    private static Map<Integer, List<Integer>> getCanInstallIndexesMap(int[][] dropRotary) {
        Map<Integer, List<Integer>> canInstallIndexesMap = new HashMap<>();
        for (int i = 0; i < COLUMNS; i++) {
            List<Integer> idxes = new ArrayList<>();
            boolean hasScatter = false;
            for (int i1 = 0; i1 < ROWS; i1++) {
                if (dropRotary[i1][i] == LotteryConfig.SCATTER) {
                    hasScatter = true;
                    break;
                }
                if (dropRotary[i1][i] == -1) {
                    idxes.add(i1 * COLUMNS + i);
                }
            }
            if (!hasScatter && !idxes.isEmpty()) {
                canInstallIndexesMap.put(i, idxes);
            }
        }
        return canInstallIndexesMap;
    }

    private static int[][] copyArr(int[][] rotary) {
        int[][] result = new int[ROWS][COLUMNS];
        for (int i = 0; i < ROWS; i++) {
            result[i] = Arrays.copyOf(rotary[i], COLUMNS);
        }
        return result;
    }

    private static int[][] copyMultiArr(int[][] rotary) {
        int[][] result = new int[COLUMNS][ROWS];
        for (int i = 0; i < COLUMNS; i++) {
            int[] rows = new int[ROWS];
            for (int i1 = 0; i1 < ROWS; i1++) {
                rows[i1] = rotary[i1][i];
            }
            result[i] = rows;
        }
        return result;
    }

    private static List<List<String>> castColumnToRow(int[][] rotary) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < COLUMNS; i++) {
            List<String> rowIcons = new ArrayList<>();
            for (int i1 = 0; i1 < ROWS; i1++) {
                rowIcons.add(String.valueOf(rotary[i1][i]));
            }
            result.add(rowIcons);
        }
        return result;
    }


    private static List<Integer> getCanUseIndexes(int[][] rotary) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            for (int i1 = 0; i1 < COLUMNS; i1++) {
                if (rotary[i][i1] != -1) continue;

                result.add(i * COLUMNS + i1);
            }
        }
        return result;
    }

    private static void installScatterAnywhere(int[][] rotary, int scatterSize) {
        if (scatterSize <= 0) {
            return;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                if (rotary[y][x] == SCATTER) {
                    continue;
                }
                if (canInstallScatter(rotary, y, x)) {
                    candidates.add(y * COLUMNS + x);
                }
            }
        }
        while (!candidates.isEmpty() && scatterSize > 0) {
            int pos = RandomUtil.nextInt(candidates.size());
            int index = candidates.remove(pos);
            int y = index / COLUMNS;
            int x = index % COLUMNS;
            rotary[y][x] = SCATTER;
            scatterSize--;
            for (int i = candidates.size() - 1; i >= 0; i--) {
                int next = candidates.get(i);
                if (!canInstallScatter(rotary, next / COLUMNS, next % COLUMNS)) {
                    candidates.remove(i);
                }
            }
        }
    }

    private static void installScatter(int[][] rotary, int scatterSize) {
        if (scatterSize <= 0) {
            return;
        }
        List<Integer> columns = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5));
        while (!columns.isEmpty() && scatterSize > 0) {
            int column = columns.remove(RandomUtil.nextInt(columns.size()));
            List<Integer> rows = new ArrayList<>();
            for (int y = 0; y < ROWS; y++) {
                if (rotary[y][column] != -1) {
                    continue;
                }
                if (canInstallScatter(rotary, y, column)) {
                    rows.add(y);
                }
            }

            if (rows.isEmpty()) {
                continue;
            }
            int y = rows.get(RandomUtil.nextInt(rows.size()));
            rotary[y][column] = SCATTER;
            scatterSize--;
        }
    }

    private static boolean canInstallScatter(int[][] rotary, int y, int x) {
        // 左
        if (x > 0 && rotary[y][x - 1] == SCATTER) {
            return false;
        }
        // 右
        if (x < COLUMNS - 1 && rotary[y][x + 1] == SCATTER) {
            return false;
        }
        // 上
        if (y > 0 && rotary[y - 1][x] == SCATTER) {
            return false;
        }
        // 下
        if (y < ROWS - 1 && rotary[y + 1][x] == SCATTER) {
            return false;
        }
        return true;
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
        List<Scene> scenes = getResultScene(betScore, factor, gamePlayer);
        if (scenes == null) {
            return null;
        }
        double totalWin = scenes.stream().map(Scene::getGold).reduce(Double::sum).get();
        this.totalWinGold = DecimalUtil.getBigDecimal2(totalWin).doubleValue();
        gamePlayer.getExtendJson().put(SCENE, scenes);
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
