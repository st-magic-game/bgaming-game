package com.bgaming.giftrush;

import com.bgaming.giftrush.entity.client.ApiClientResult;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

import static com.bgaming.giftrush.logic.GiftRushContext.generateApiResult;

@Slf4j
@SpringBootApplication
public class Application implements ApplicationRunner {

    public static void main(String[] args) {
        log.info("bGaming gift-rush-server:version:20260616");
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

        int num = 100000;
        int stake = 1;
        boolean bonus_buy = false;
        double v = 1;

        int prizeNum = 0;
        int freeNum = 0;
        double totalBet = 0;
        double totalPayout = 0;
        double bonusTotalPayout = 0;
        for (int i = 0; i < num; i++) {
            ApiClientResult clientResult = generateApiResult(stake, v, bonus_buy);
            if (bonus_buy) {
                totalBet += (stake * 80);
            } else {
                totalBet += stake;
            }
            Map<Integer, List<List<Integer>>> scatter = clientResult.getOutcome().getSpecial_symbols().getScatter();
            if (scatter.get(8).size() == 3) {
                freeNum++;
                bonusTotalPayout += clientResult.getOutcome().getWin().doubleValue();
            }
            if (clientResult.getOutcome().getWin().doubleValue() >  0) {
                totalPayout += clientResult.getOutcome().getWin().doubleValue();
                prizeNum++;
            }
        }
        log.info("中奖率： {}，免费概率：{}",((double)prizeNum / num),((double)freeNum / num));
        log.info("下注分数： {}，中奖分数：{}，免费分数：{}",totalBet,totalPayout,bonusTotalPayout);
        log.info("rtp： {}，免费rtp：{}",(totalPayout/totalBet),(bonusTotalPayout / totalPayout));
    }
}
