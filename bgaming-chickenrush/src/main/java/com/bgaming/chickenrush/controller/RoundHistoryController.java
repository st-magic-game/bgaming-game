package com.bgaming.chickenrush.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bgaming.chickenrush.entity.log.RoundDetailDto;
import com.bgaming.chickenrush.entity.log.RoundHistoryDto;
import com.bgaming.chickenrush.utils.DateTimeUtil;
import com.bgaming.chickenrush.utils.JwtUtil;
import com.game.base.application.service.GameService;
import com.game.base.common.util.DecimalUtil;
import com.game.base.common.util.TimeUtil;
import com.game.base.domain.order.OrderPage;
import com.game.base.domain.order.OrderRecord;
import com.game.base.infrastructure.persistence.entity.User;
import com.game.base.infrastructure.persistence.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.game.base.common.constant.Protocol.GAME_SUB_LOG_DETAIL;
import static com.game.base.common.constant.Protocol.GAME_SUB_LOG_LIST;

@Slf4j
@Controller
@RequestMapping("/api/rounds_history")
public class RoundHistoryController {
    private final GameService gameService;
    private final UserMapper userMapper;

    public static String ORIGIN;

    @Autowired
    public RoundHistoryController(GameService gameService, UserMapper userMapper) {
        this.gameService = gameService;
        this.userMapper = userMapper;
    }

    @GetMapping("/{token}")
    public String history(@PathVariable String token, Model model,HttpServletRequest request) {
        log.info("receive history token {}", token);
        List<RoundHistoryDto> list = new ArrayList<>();
        try {
            String uuid = token.split("\\.")[0];
            User account = this.userMapper.findAccount(uuid);
            int userId = account.getUserID();
            JSONObject data = new JSONObject();
            data.put("pageSize",10000);
            data.put("startTime",TimeUtil.getNow() - 7 * 24 * 60 * 60 * 1000); // 查七天
            data.put("endTime", TimeUtil.getNow());
            OrderPage spinResult = (OrderPage) this.gameService.doWithOutLock(userId, GAME_SUB_LOG_LIST, data.toJSONString());
            if (spinResult != null) {
                List<OrderRecord> betList = spinResult.getBetList();
                log.info("userId {} . history resultSize {}", userId, betList.size());
                for (OrderRecord orderRecord : betList) {
                    String recordToken = generateToken(orderRecord, userId);
                    RoundHistoryDto history = RoundHistoryDto.builder()
                            .dateTime(DateTimeUtil.parseDateTime(orderRecord.getOrderTime()))
                            .bet(orderRecord.getStake().toPlainString())
                            .totalWin(orderRecord.getPayout().toPlainString())
                            .profit(orderRecord.getWinLose().toPlainString())
                            .balanceBefore(DecimalUtil.getBigDecimal2(orderRecord.getLogoutScore().doubleValue()
                                    - orderRecord.getWinLose().doubleValue()).toPlainString())
                            .balanceAfter(orderRecord.getLogoutScore().toPlainString())
                            .currency(orderRecord.getCoinType())
                            .token(recordToken)
                            .roundId(orderRecord.getRowId())
                            .build();
                    list.add(history);
                }
            }
        } catch (Exception e) {
            log.error("token {} , history error:", token, e);
        }
        ORIGIN = request.getParameter("clientBase");
        model.addAttribute("clientBase", ORIGIN != null ? ORIGIN : "");
        model.addAttribute("gameName", "Chicken Rush");
        model.addAttribute("histories", list);
        return "history";
    }

    private static String generateToken(OrderRecord orderRecord, int userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pl_id", userId);
        payload.put("r_id", orderRecord.getRowId());
        payload.put("r_ts", orderRecord.getOrderTime().toEpochSecond(ZoneOffset.UTC));
        Map<String, Object> claims = new HashMap<>();
        claims.put("data", payload);
        claims.put("exp", TimeUtil.getNow() + 24 * 60 * 60 * 1000);
        return JwtUtil.generateToken(claims);
    }

    @GetMapping("/-/{token}")
    public String roundDetail(@PathVariable String token, Model model) {
        RoundDetailDto roundDetailDto = null;
        try {
            Claims claims = JwtUtil.parseToken(token);
            Map<String,Object> map = claims.get("data", Map.class);
            Integer userId = (Integer) map.get("pl_id");
            Long rowId = Long.valueOf((String.valueOf(map.get("r_id"))));
            JSONObject data = new JSONObject();
            data.put("rowId", rowId);
            JSONObject jsonObj = (JSONObject) this.gameService.doWithOutLock(userId, GAME_SUB_LOG_DETAIL, data.toJSONString());
            String cleanJson = JSON.toJSONString(jsonObj,SerializerFeature.DisableCircularReferenceDetect);
//            cleanJson = "{\"profitText\":\"7.95\",\"balanceBefore\":10000,\"usedFeature\":false,\"balanceAfterText\":\"10007.95\",\"totalWin\":8.2,\"bet\":0.25,\"stake\":\"0\",\"balanceBeforeText\":\"10000\",\"clientResults\":[{\"features\":{\"freespins_issued\":15,\"freespins_left\":15},\"balance\":{\"game\":615,\"wallet\":998000},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"freespin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"closed\",\"last_action_id\":\"13178-212035-27060472_1\",\"command\":\"spin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",5,[[0,0],[1,0],[1,2],[4,0],[4,1],[5,1],[5,2],[5,3]],\"9\"],[\"scatter\",200,[[0,4],[1,3],[2,3],[2,4]]],[\"bombs_win\",410]],\"freespins_issued\":15,\"special_symbols\":{\"scatter\":{\"0\":[[0,4],[1,3],[2,3],[2,4]]}},\"screen\":[[9,3,3,8,0],[9,3,9,0,4],[6,3,3,0,0],[1,6,4,2,8],[9,9,8,2,5],[1,9,9,9,7]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[9,3,3,8,0],[9,3,9,0,4],[6,3,3,0,0],[1,6,4,2,8],[9,9,8,2,5],[1,9,9,9,7]],[[5,3,3,8,0],[1,12,3,0,4],[6,3,3,0,0],[1,6,4,2,8],[5,9,8,2,5],[3,7,6,1,7]]],\"total_multiplier\":3,\"bombs\":[[\"cascade_1\",3,[1,1]]],\"round_multiplier\":3},\"win\":615}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":19},\"balance\":{\"game\":615,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_2\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":5,\"special_symbols\":{\"scatter\":{\"0\":[[0,0],[4,1],[4,3]]}},\"screen\":[[0,9,4,2,2],[7,2,9,2,2],[7,2,1,9,9],[7,6,4,5,1],[1,0,2,0,1],[9,9,8,6,6]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[0,9,4,2,2],[7,2,9,2,2],[7,2,1,9,9],[7,6,4,5,1],[1,0,2,0,1],[9,9,8,6,6]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":18},\"balance\":{\"game\":620,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_3\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",5,[[0,0],[0,1],[2,0],[2,1],[3,0],[4,2],[4,3],[4,4]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[9,9,8,5,6],[7,6,1,8,2],[9,9,4,7,4],[9,1,1,3,3],[8,5,9,9,9],[8,6,2,3,4]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[9,9,8,5,6],[7,6,1,8,2],[9,9,4,7,4],[9,1,1,3,3],[8,5,9,9,9],[8,6,2,3,4]],[[1,3,8,5,6],[7,6,1,8,2],[9,4,4,7,4],[1,1,1,3,3],[4,9,4,8,5],[8,6,2,3,4]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":5}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":17},\"balance\":{\"game\":641,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_4\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",16,[[0,0],[0,2],[1,4],[2,3],[2,4],[4,0],[4,2],[5,0],[5,3]],\"6\"],[\"cascade_1\",5,[[0,2],[0,3],[2,0],[2,1],[2,3],[4,1],[4,2],[5,0],[5,1]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[6,9,6,9,3],[8,4,2,1,6],[8,9,4,6,6],[7,3,2,5,8],[6,9,6,7,8],[6,5,2,6,3]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[6,9,6,9,3],[8,4,2,1,6],[8,9,4,6,6],[7,3,2,5,8],[6,9,6,7,8],[6,5,2,6,3]],[[6,4,9,9,3],[8,8,4,2,1],[9,9,8,9,4],[7,3,2,5,8],[8,9,9,7,8],[9,9,5,2,3]],[[4,1,6,4,3],[8,8,4,2,1],[5,9,1,8,4],[7,3,2,5,8],[8,9,8,7,8],[4,7,5,2,3]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":21}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":16},\"balance\":{\"game\":641,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_5\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[4,2,4,3,5],[8,5,5,1,4],[2,3,8,5,9],[9,2,3,7,8],[4,2,9,6,7],[7,8,7,2,9]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[4,2,4,3,5],[8,5,5,1,4],[2,3,8,5,9],[9,2,3,7,8],[4,2,9,6,7],[7,8,7,2,9]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":15},\"balance\":{\"game\":661,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_6\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",20,[[0,2],[0,3],[1,3],[2,1],[2,2],[3,0],[4,1],[4,3],[5,1],[5,4]],\"7\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[3,2],[4,4]]}},\"screen\":[[5,8,7,7,9],[9,1,8,7,1],[3,7,7,1,3],[7,6,0,1,6],[4,7,8,7,0],[4,7,8,9,7]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[5,8,7,7,9],[9,1,8,7,1],[3,7,7,1,3],[7,6,0,1,6],[4,7,8,7,0],[4,7,8,9,7]],[[5,7,5,8,9],[9,9,1,8,1],[2,1,3,1,3],[1,6,0,1,6],[5,1,4,8,0],[5,4,4,8,9]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":20}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":14},\"balance\":{\"game\":666,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_7\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",5,[[0,3],[1,2],[1,3],[2,1],[2,3],[4,2],[4,4],[5,0],[5,2]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[2,2]]}},\"screen\":[[1,4,6,9,3],[2,3,9,9,1],[0,9,7,9,4],[5,4,3,8,1],[3,3,9,8,9],[9,7,9,4,7]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[1,4,6,9,3],[2,3,9,9,1],[0,9,7,9,4],[5,4,3,8,1],[3,3,9,8,9],[9,7,9,4,7]],[[6,1,4,6,3],[2,5,2,3,1],[5,9,0,7,4],[5,4,3,8,1],[7,6,3,3,8],[6,8,7,4,7]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":5}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":13},\"balance\":{\"game\":666,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_8\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[3,3]]}},\"screen\":[[4,2,3,8,3],[8,9,8,7,7],[8,9,5,8,12],[6,9,2,0,4],[5,4,2,3,7],[9,6,6,9,2]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[4,2,3,8,3],[8,9,8,7,7],[8,9,5,8,12],[6,9,2,0,4],[5,4,2,3,7],[9,6,6,9,2]]],\"total_multiplier\":3,\"bombs\":[[\"cascade_0\",3,[2,4]]],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":12},\"balance\":{\"game\":671,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_9\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",5,[[0,0],[0,2],[0,4],[1,0],[1,3],[2,1],[3,4],[4,0],[5,2]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[3,4]]}},\"screen\":[[9,4,9,3,9],[9,6,2,9,2],[6,9,3,8,8],[5,8,4,0,9],[9,1,4,3,1],[6,2,9,4,4]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[9,4,9,3,9],[9,6,2,9,2],[6,9,3,8,8],[5,8,4,0,9],[9,1,4,3,1],[6,2,9,4,4]],[[7,6,3,4,3],[9,8,6,2,2],[7,6,3,8,8],[8,5,8,4,0],[9,1,4,3,1],[3,6,2,4,4]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":5}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":11},\"balance\":{\"game\":676,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_10\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",5,[[0,1],[1,4],[2,2],[3,0],[3,4],[4,1],[4,2],[5,4]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[4,9,2,7,1],[3,7,5,3,9],[2,6,9,1,8],[9,3,1,3,9],[3,9,9,4,8],[7,4,7,2,9]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[4,9,2,7,1],[3,7,5,3,9],[2,6,9,1,8],[9,3,1,3,9],[3,9,9,4,8],[7,4,7,2,9]],[[7,4,2,7,1],[3,3,7,5,3],[4,2,6,1,8],[6,4,3,1,3],[2,3,3,4,8],[8,7,4,7,2]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":5}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":10},\"balance\":{\"game\":676,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_11\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[0,0]]}},\"screen\":[[0,5,5,8,2],[8,6,9,8,2],[7,3,3,6,8],[1,8,9,1,9],[4,9,9,4,5],[7,1,1,4,1]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[0,5,5,8,2],[8,6,9,8,2],[7,3,3,6,8],[1,8,9,1,9],[4,9,9,4,5],[7,1,1,4,1]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":9},\"balance\":{\"game\":700,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_12\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",24,[[0,2],[1,0],[1,1],[2,1],[3,2],[3,4],[4,2],[4,4],[5,0],[5,2],[5,4]],\"6\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[5,9,6,2,4],[6,6,2,4,4],[3,6,7,1,8],[5,1,6,5,6],[7,7,6,2,6],[6,9,6,8,6]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[5,9,6,2,4],[6,6,2,4,4],[3,6,7,1,8],[5,1,6,5,6],[7,7,6,2,6],[6,9,6,8,6]],[[7,5,9,2,4],[5,8,2,4,4],[5,3,7,1,8],[8,4,5,1,5],[2,9,7,7,2],[3,9,2,9,8]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":24}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":8},\"balance\":{\"game\":740,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_13\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",40,[[0,3],[0,4],[1,2],[1,3],[2,3],[3,3],[4,0],[4,2],[4,3],[5,0],[5,1],[5,3]],\"9\"]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[0,2],[0,4]]}},\"screen\":[[0,6,0,9,9],[3,7,9,9,5],[5,3,8,9,6],[6,1,2,9,3],[9,6,9,9,1],[9,9,1,9,7]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[0,6,0,9,9],[3,7,9,9,5],[5,3,8,9,6],[6,1,2,9,3],[9,6,9,9,1],[9,9,1,9,7]],[[2,4,0,6,0],[8,1,3,7,5],[1,5,3,8,6],[4,6,1,2,3],[3,7,2,6,1],[6,1,9,1,7]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":40}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":7},\"balance\":{\"game\":740,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_14\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[6,8,1,9,1],[2,9,7,5,9],[6,8,9,8,5],[7,6,6,9,6],[1,1,1,4,6],[1,2,8,8,6]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[6,8,1,9,1],[2,9,7,5,9],[6,8,9,8,5],[7,6,6,9,6],[1,1,1,4,6],[1,2,8,8,6]]],\"total_multiplier\":3,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":6},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_15\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[[\"cascade_0\",16,[[0,1],[0,4],[1,2],[2,0],[2,2],[2,3],[3,4],[4,2]],\"6\"],[\"bombs_win\",64]],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[7,6,9,3,6],[1,4,6,4,5],[6,8,6,6,8],[1,9,3,5,6],[9,1,6,2,2],[2,12,7,1,5]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[7,6,9,3,6],[1,4,6,4,5],[6,8,6,6,8],[1,9,3,5,6],[9,1,6,2,2],[2,12,7,1,5]],[[9,8,7,9,3],[6,1,4,4,5],[6,1,7,8,8],[4,1,9,3,5],[9,9,1,2,2],[2,12,7,1,5]]],\"total_multiplier\":5,\"bombs\":[[\"cascade_0\",2,[5,1]]],\"round_multiplier\":2},\"win\":80}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":5},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_16\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[1,7,9,6,2],[5,2,9,4,1],[7,7,8,5,3],[1,2,9,5,3],[8,7,1,7,9],[3,9,5,1,8]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[1,7,9,6,2],[5,2,9,4,1],[7,7,8,5,3],[1,2,9,5,3],[8,7,1,7,9],[3,9,5,1,8]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":4},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_17\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[6,9,4,5,3],[8,1,1,2,3],[7,9,8,4,5],[9,4,9,1,9],[7,4,2,7,1],[1,7,7,1,9]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[6,9,4,5,3],[8,1,1,2,3],[7,9,8,4,5],[9,4,9,1,9],[7,4,2,7,1],[1,7,7,1,9]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":3},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_18\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[1,6,1,7,7],[9,3,4,4,4],[2,8,4,9,7],[7,9,9,1,4],[9,9,4,7,8],[4,7,9,1,8]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[1,6,1,7,7],[9,3,4,4,4],[2,8,4,9,7],[7,9,9,1,4],[9,9,4,7,8],[4,7,9,1,8]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":2},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_19\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[6,1,9,3,7],[4,7,5,8,1],[9,2,6,2,2],[4,4,9,6,8],[6,3,2,1,2],[6,9,7,5,9]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[6,1,9,3,7],[4,7,5,8,1],[9,2,6,2,2],[4,4,9,6,8],[6,3,2,1,2],[6,9,7,5,9]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":1},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"freespins\",\"last_action_id\":\"13178-212035-27060472_20\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[]}},\"screen\":[[1,2,9,6,5],[3,1,4,9,8],[3,9,4,5,5],[4,7,4,1,3],[1,8,8,9,4],[5,4,6,5,1]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[1,2,9,6,5],[3,1,4,9,8],[3,9,4,5,5],[4,7,4,1,3],[1,8,8,9,4],[5,4,6,5,1]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}},{\"features\":{\"freespins_issued\":20,\"freespins_left\":0},\"balance\":{\"game\":820,\"wallet\":999980},\"api_version\":\"2\",\"flow\":{\"round_id\":\"13178-212035-27060472\",\"available_actions\":[\"init\",\"spin\"],\"purchased_feature\":{\"name\":\"freespin_buy\"},\"state\":\"closed\",\"last_action_id\":\"13178-212035-27060472_21\",\"command\":\"freespin\"},\"outcome\":{\"bet\":20,\"wins\":[],\"freespins_issued\":0,\"special_symbols\":{\"scatter\":{\"0\":[[2,3]]}},\"screen\":[[2,7,9,4,7],[1,2,4,3,5],[6,5,9,0,2],[7,7,7,1,6],[5,1,8,2,7],[4,9,9,9,8]],\"storage\":{\"super_spin\":false,\"saved_screens\":[[[2,7,9,4,7],[1,2,4,3,5],[6,5,9,0,2],[7,7,7,1,6],[5,1,8,2,7],[4,9,9,9,8]]],\"total_multiplier\":5,\"bombs\":[],\"round_multiplier\":0},\"win\":0}}],\"betText\":\"0.25\",\"totalWinText\":\"8.2\",\"balanceAfter\":10007.95,\"currency\":\"BRL\",\"time\":\"2026-06-2217:27:10UTC+00:00\",\"bonusMultiplier\":0,\"profit\":7.95}";
            roundDetailDto = JSONObject.parseObject(cleanJson, RoundDetailDto.class);

        } catch (Exception e) {
            log.error("token {} history detail error:", token, e);
        }
        model.addAttribute("clientBase", ORIGIN != null ? ORIGIN : "");
        model.addAttribute("detail", roundDetailDto);
        return "round_detail";
    }

    private String fixJson(String raw) {
        Pattern p = Pattern.compile("(-?\\d+\\.?\\d*)[Ee]([+-]?\\d+)");
        Matcher m = p.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double val = Double.parseDouble(m.group());
            String replacement = (val == Math.floor(val) && !Double.isInfinite(val))
                    ? String.valueOf((long) val)
                    : String.valueOf(val);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}