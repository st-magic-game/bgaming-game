package com.bgaming.bonanzabillion.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

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