package com.bgaming.alienfruits2.entity.client;

import com.alibaba.fastjson.JSONObject;
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

    private JSONObject purchased_feature = new JSONObject();

    private String round_id;

    private String state = "closed";
}
