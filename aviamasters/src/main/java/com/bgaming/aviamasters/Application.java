package com.bgaming.aviamasters;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.aviamasters.entity.client.ApiClientResult;
import com.bgaming.aviamasters.logic.AviamastersRoundModeFinder;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

import static com.bgaming.aviamasters.logic.AviamastersContext.roundProcessResult;

@Slf4j
@SpringBootApplication
public class Application implements ApplicationRunner {

    public static void main(String[] args) {
        log.info("bGaming aviamasters-server:version:20260626");
        SpringApplication.run(Application.class);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        test();
    }

    private void test() {
        Player player = new Player();
        User user = new User();
        user.setScore(100000);
        player.setUser(user);

        int num = 10000;
        long stake = 50;
        double v = 0.87;

        int prizeNum = 0;
        double totalBet = 0;
        double totalPayout = 0;
        for (int i = 0; i < num; i++) {
            AviamastersRoundModeFinder.RoundProcessResult roundProcessResult = roundProcessResult(stake, v, "1");
            totalBet += stake;
            long win = roundProcessResult.getWin();
            if (win > 0) {
                totalPayout += win;
                prizeNum++;
            }
        }
        log.info("中奖率： {},中奖次数：{}",((double)prizeNum / num),prizeNum);
        log.info("下注分数： {}，中奖分数：{}",totalBet,totalPayout);
        log.info("rtp： {}",(totalPayout/totalBet));
    }
}
