package com.bgaming.alienfruits2.entity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data@NoArgsConstructor@AllArgsConstructor
public class SpecialSymbols implements Serializable {

    private Map<String, List<int[]>> scatter = new HashMap<>();
}
