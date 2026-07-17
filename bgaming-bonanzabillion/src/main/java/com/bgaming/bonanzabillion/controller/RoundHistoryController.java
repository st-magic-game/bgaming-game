package com.bgaming.bonanzabillion.controller;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.bonanzabillion.entity.dto.RoundDetailDto;
import com.bgaming.bonanzabillion.entity.dto.RoundHistoryDto;
import com.bgaming.bonanzabillion.utils.DateTimeUtil;
import com.bgaming.bonanzabillion.utils.JwtUtil;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.*;

import static com.game.base.common.constant.Protocol.GAME_SUB_LOG_DETAIL;
import static com.game.base.common.constant.Protocol.GAME_SUB_LOG_LIST;

@Slf4j
@Controller
@RequestMapping("/api/rounds_history")
public class RoundHistoryController {
    private final GameService gameService;
    private final UserMapper userMapper;
    public static String ORIGIN;

    @Value("${game.gameName:Bonanza Billion}")
    private String gameName;

    @Autowired
    public RoundHistoryController(GameService gameService, UserMapper userMapper) {
        this.gameService = gameService;
        this.userMapper = userMapper;
    }

    @GetMapping("/{token}")
    public String history(@PathVariable String token, Model model, HttpServletRequest request) {
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
                            .bet(orderRecord.getStake().stripTrailingZeros().toPlainString())
                            .totalWin(orderRecord.getPayout().stripTrailingZeros().toPlainString())
                            .profit(orderRecord.getWinLose().stripTrailingZeros().toPlainString())
                            .balanceBefore(DecimalUtil.getBigDecimal2(orderRecord.getLogoutScore().doubleValue()
                                    - orderRecord.getWinLose().doubleValue()).stripTrailingZeros().toPlainString())
                            .balanceAfter(orderRecord.getLogoutScore().stripTrailingZeros().toPlainString())
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
        model.addAttribute("gameName", gameName);
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
        List<RoundDetailDto> details = new ArrayList<>();
        RoundDetailDto roundDetailDto = new RoundDetailDto();
        try {
            Claims claims = JwtUtil.parseToken(token);
            Map<String,Object> map = claims.get("data", Map.class);
            Integer userId = (Integer) map.get("pl_id");
            Long rowId = Long.valueOf((String.valueOf(map.get("r_id"))));
            JSONObject data = new JSONObject();
            data.put("rowId", rowId);
            details = (List<RoundDetailDto>) this.gameService.doWithOutLock(userId, GAME_SUB_LOG_DETAIL, data.toJSONString());
            if(details.size() == 1){
                roundDetailDto = details.get(0);
            }else{
                RoundDetailDto start = details.get(0);
                RoundDetailDto end = details.get(details.size() - 1);
                BigDecimal totalWin = details.stream().map(RoundDetailDto::getTotalRoundWin).reduce(BigDecimal::add).get();
                roundDetailDto.setBalanceAfterText(end.getBalanceAfterText());
                roundDetailDto.setBalanceBeforeText(start.getBalanceBeforeText());
                roundDetailDto.setProfitText(totalWin.subtract(start.getBet()).stripTrailingZeros().toPlainString());
                roundDetailDto.setTotalWin(totalWin);
                roundDetailDto.setTotalWinText(totalWin.stripTrailingZeros().toPlainString());
                roundDetailDto.setCurrency(start.getCurrency());
                roundDetailDto.setTime(end.getTime());
                roundDetailDto.setFeatureName(start.getFeatureName());
            }
            roundDetailDto.setBetText(details.get(0).getBet().stripTrailingZeros().toPlainString());
        } catch (Exception e) {
            log.error("token {} history detail error:", token, e);
        }
        model.addAttribute("clientBase", ORIGIN != null ? ORIGIN : "");
        model.addAttribute("gameName", gameName);
        model.addAttribute("details", details);
        model.addAttribute("detail", roundDetailDto);
        return "round_detail";
    }
}