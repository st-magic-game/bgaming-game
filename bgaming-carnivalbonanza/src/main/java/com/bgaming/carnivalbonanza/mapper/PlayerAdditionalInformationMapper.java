package com.bgaming.carnivalbonanza.mapper;

import com.bgaming.carnivalbonanza.entity.PlayerAdditionalInformation;
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
            "INSERT INTO " + TABLE_NAME + " (userid, lastUi,betScore,beforeScore,freeNum,totalFreeNum,usedFeature,updateTime) " +
                    "VALUES (#{userId}, #{lastUi}, #{betScore},#{beforeScore}, #{freeNum},#{totalFreeNum},#{usedFeature}, #{updateTime}) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "lastUi = #{lastUi}," +
                    "freeNum = #{freeNum}," +
                    "totalFreeNum = #{totalFreeNum}," +
                    "usedFeature = #{usedFeature}," +
                    "betScore = #{betScore}" +
                    "beforeScore = #{beforeScore}"
    })
    int upsertLastUiByUserId(PlayerAdditionalInformation pai);
}
