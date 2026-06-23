package com.bgaming.alienfruits2.entity.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundHistoryDto {

    private String dateTime;

    private String bet;

    private String totalWin;

    private String profit;

    private String balanceBefore;

    private String balanceAfter;

    private String currency;

    private String roundLink;

    private String token;

    private Long roundId;
}