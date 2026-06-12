package com.bgaming.giftrush.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data@NoArgsConstructor@AllArgsConstructor
public class PayLines {

    private int id;

    private List<Integer> positions = new ArrayList<>();
}
