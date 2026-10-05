package com.fc.v2.mapper.custom;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 改造进度卡代号按季取号。
 * 卡代号只从区里这一处起：INSERT IGNORE 保底有行，UPDATE … LAST_INSERT_ID 在本连接内取号，
 * 并发各拿各的号，本子上自带的号递来一概不认。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Mapper
public interface BarrCardSeqDao {

    /** 该季取号行没有就先立一行（0 起），有则不动 */
    @Insert("INSERT IGNORE INTO t_barr_card_seq(season, next_val) VALUES(#{season}, 0)")
    int ensureSeason(@Param("season") String season);

    /** 本连接内把号往前挪一格，并把挪到的值记进 LAST_INSERT_ID */
    @Update("UPDATE t_barr_card_seq SET next_val = LAST_INSERT_ID(next_val + 1) WHERE season = #{season}")
    int bump(@Param("season") String season);

    /** 取出本连接刚挪到的号 */
    @Select("SELECT LAST_INSERT_ID()")
    long currentValue();
}
