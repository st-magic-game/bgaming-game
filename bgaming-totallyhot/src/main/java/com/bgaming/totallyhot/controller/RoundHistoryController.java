package com.bgaming.totallyhot.controller;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.totallyhot.entity.dto.RoundDetailDto;
import com.bgaming.totallyhot.entity.dto.RoundHistoryDto;
import com.bgaming.totallyhot.utils.JwtUtil;
import com.game.base.application.service.GameService;
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
import java.time.format.DateTimeFormatter;
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
                            .dateTime(orderRecord.getOrderTime().format(
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    + " UTC+00:00")
                            .bet(orderRecord.getStake().toPlainString())
                            .totalWin(orderRecord.getPayout().stripTrailingZeros().toPlainString())
                            .profit(orderRecord.getWinLose().stripTrailingZeros().toPlainString())
                            .balanceBefore(orderRecord.getLoginScore().stripTrailingZeros().toPlainString())
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
        model.addAttribute("gameName", "Totally Hot");
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
        RoundDetailDto detail = new RoundDetailDto();
        try {
            Claims claims = JwtUtil.parseToken(token);
            Map<String,Object> map = claims.get("data", Map.class);
            Integer userId = (Integer) map.get("pl_id");
            Long rowId = Long.valueOf((String.valueOf(map.get("r_id"))));
            JSONObject data = new JSONObject();
            data.put("rowId", rowId);
            detail = (RoundDetailDto) this.gameService.doWithOutLock(userId, GAME_SUB_LOG_DETAIL, data.toJSONString());
        } catch (Exception e) {
            log.error("token {} history detail error:", token, e);
        }
        model.addAttribute("clientBase", ORIGIN != null ? ORIGIN : "");
        model.addAttribute("detail", detail);
        return "round_detail";
    }
}