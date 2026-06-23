package com.bgaming.alienfruits2;

import com.alibaba.fastjson.JSONObject;
import com.bgaming.alienfruits2.entity.client.ApiClientResult;
import com.bgaming.alienfruits2.logic.AlienFruits2Context;
import com.game.base.domain.player.Player;
import com.game.base.infrastructure.persistence.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;

@Slf4j
@MapperScan(value = {"com.bgaming.alienfruits2.mapper"})
@SpringBootApplication
public class Application implements ApplicationRunner {

    public static void main(String[] args) {
        log.info("bGaming AlienFruits2-server:version:20260611");
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
        double stake = 20;
        boolean bonus_buy = false;
        boolean freespin_chance = true;
        double v = 1.25;

        int prizeNum = 0;
        int freeNum = 0;
        double totalBet = 0;
        double totalPayout = 0;
        double bonusTotalPayout = 0;
        boolean use = true;
        int spinNum = 0;
        for (int i = 0; i < num; i++) {
            List<ApiClientResult> clientResult = AlienFruits2Context.generateApiResult(player,stake, v, bonus_buy,freespin_chance,"1");
            if (use) {
                if (bonus_buy) {
                    totalBet += (stake * 100);
                } else if(freespin_chance){
                    totalBet += (stake * 1.25);
                }else {
                    totalBet += stake;
                }
                spinNum++;
            }
            Map<String, List<int[]>> scatter = clientResult.get(clientResult.size() - 1).getOutcome().getSpecial_symbols().getScatter();
            ApiClientResult apiClientResult = clientResult.get(clientResult.size() - 1);
            JSONObject features = apiClientResult.getFeatures();
            if (features != null && features.getInteger("freespins_left") >= 0) {
                use = false;
                freespin_chance = false;
                bonusTotalPayout += apiClientResult.getOutcome().getWin().doubleValue();
                if (features.getInteger("freespins_left") == 0) {
                    player.getExtendJson().clear();
                    use = true;
                    freespin_chance = true;
                }
            } else {
                player.getExtendJson().clear();
                use = true;
                freespin_chance = true;
            }

            if (scatter != null && scatter.get("0").size() >= 4) {
                freeNum++;
            }
            if (apiClientResult.getOutcome().getWin().doubleValue() >  0) {
                totalPayout += apiClientResult.getOutcome().getWin().doubleValue();
                prizeNum++;
            }
        }
        log.info("中奖率： {}，免费概率：{},中奖次数：{}",((double)prizeNum / spinNum),((double)freeNum / spinNum),prizeNum);
        log.info("下注分数： {}，中奖分数：{}，免费分数：{}",totalBet,totalPayout,bonusTotalPayout);
        log.info("rtp： {}，免费rtp：{}",(totalPayout/totalBet),(bonusTotalPayout / totalPayout));
    }
}
