package com.bgaming.aztecclusters.mapper;


import com.bgaming.aztecclusters.entity.PlayerAdditionalInformation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 玩家附加信息
 */
@Mapper
public interface PlayerAdditionalInformationMapper {

    String TABLE_NAME = " ps_account_additional_information ";

    @Select(" SELECT * FROM" + TABLE_NAME + "WHERE userid = #{userId}")
    PlayerAdditionalInformation getAdditionalInformation(@Param("userId") int userId);

    @Insert({
            "INSERT INTO " + TABLE_NAME + " (userid, lastUi,playTimes,betScore,scenes,updateTime) " +
                    "VALUES (#{userId}, #{lastUi}, #{playTimes},#{betScore}, #{scenes}, #{updateTime}) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "lastUi = #{lastUi}," +
                    "playTimes = #{playTimes}," +
                    "scenes = #{scenes}," +
                    "betScore = #{betScore}"
    })
    int upsertLastUiByUserId(PlayerAdditionalInformation pai);
}
