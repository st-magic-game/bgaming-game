package com.bgaming.alienfruits2.entity.client;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class WinLine implements Serializable {

    private int symbol;

    private int count = 3;

    private int odds;

    private BigDecimal payout = BigDecimal.ZERO;

    private int winPatternId;

    private List<Integer> winPositions = new ArrayList<>();
}
