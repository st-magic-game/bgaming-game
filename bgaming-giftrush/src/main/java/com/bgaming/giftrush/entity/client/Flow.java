package com.bgaming.giftrush.entity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
@Data@NoArgsConstructor@AllArgsConstructor@Accessors(chain = true)
public class Flow implements Serializable {

    private static final long serialVersionUID = 1L;

    private String[] available_actions = new String[] {"init","spin"};

    private String command = "spin";

    private String last_action_id;

    private Object purchased_feature = new Object();

    private String round_id;

    private String state = "closed";
}
