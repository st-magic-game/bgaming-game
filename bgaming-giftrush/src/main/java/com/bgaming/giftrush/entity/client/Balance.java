package com.bgaming.giftrush.entity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data@NoArgsConstructor@AllArgsConstructor
public class Balance implements Serializable {

    private BigDecimal game = BigDecimal.ZERO;

    private BigDecimal wallet = BigDecimal.ZERO;
}
