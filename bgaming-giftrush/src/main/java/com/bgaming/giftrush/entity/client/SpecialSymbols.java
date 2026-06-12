package com.bgaming.giftrush.entity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data@NoArgsConstructor@AllArgsConstructor
public class SpecialSymbols implements Serializable {

    private Map<Integer, List<List<Integer>>> scatter = new HashMap<>();
}
