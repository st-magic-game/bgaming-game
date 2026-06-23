package com.bgaming.bonanzabillion.handlers;

import com.alibaba.fastjson.JSONObject;
import com.game.base.application.handler.BGamingCommandHandler;
import com.game.base.application.service.GameService;
import com.game.base.interfaces.dto.bgaming.BGamingCommandRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
@Slf4j
public class BGamingFreeSpinCommandHandler implements BGamingCommandHandler {
    private final GameService gameService;

    @Autowired
    public BGamingFreeSpinCommandHandler(GameService gameService) {
        this.gameService = gameService;
    }

    public String commandType() {
        return "freespin";
    }

    public Object handle(HttpServletRequest request, BGamingCommandRequest commandRequest) {
        Integer userId = (Integer)request.getAttribute("userId");
        if (userId == null) {
            throw new InvalidPropertyException(Integer.class, "userId", "user not exist!");
        } else {
            String guestId = request.getHeader("authorization");
            log.info("收到消息spin - guestId: {} msg: {}", guestId, JSONObject.toJSONString(commandRequest));

            try {
                Object spinResult = this.gameService.doSpin(userId, JSONObject.toJSONString(commandRequest));
                if (spinResult != null) {
                    log.info("userId {} . spin result {}", userId, spinResult);
                    return spinResult;
                }

                log.error("userid = {},free开奖报错", userId);
            } catch (Exception var6) {
                log.error("userId {} ,spin error:", userId, var6);
            }

            throw new InvalidPropertyException(BGamingCommandRequest.class, "commandRequest", "user spin error!");
        }
    }
}