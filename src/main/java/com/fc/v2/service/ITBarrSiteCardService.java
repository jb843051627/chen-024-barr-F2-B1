package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBarrCardLog;
import com.fc.v2.model.auto.TBarrSiteCard;
import com.fc.v2.model.custom.CardSummary;
import com.fc.v2.model.custom.SiteCardForm;
import com.fc.v2.model.custom.SiteCardQuery;

/**
 * 改造进度卡 Service接口。
 * 卡只从区里这一处起：卡代号系统排、差口系统轧、档系统数；底册不动卡不动。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITBarrSiteCardService {

    /** 按主键查询（旧卡空字段就地按在走接住，不报错） */
    TBarrSiteCard selectTBarrSiteCardById(Long id);

    /** 按条件查询列表（分页由调用方统一 startPage；只回 del_flag=0） */
    List<TBarrSiteCard> selectTBarrSiteCardList(Wrapper<TBarrSiteCard> queryWrapper);

    /** 按寻卡三头与可见街道拼查询条件（前缀/整段皆可） */
    Wrapper<TBarrSiteCard> buildWrapper(SiteCardQuery query, List<String> visibleStreetCodes);

    /**
     * 立卡（亲手立 / 当月验收带出同走这一处）。
     * 卡代号系统排、create_by 落登录身份、差口当场轧；同点位已有在走卡则挡住。
     */
    TBarrSiteCard createCard(SiteCardForm form, String operator);

    /** 改口编辑：收口卡一字改不动；不把眼下手这张当成第二张挡门外；换街重轧差口 */
    TBarrSiteCard updateCard(SiteCardForm form, String operator);

    /** 走完一级：新录→已改齐(缺项)；这一级没走完不给走下一级；档按级数与处数自数 */
    TBarrSiteCard advance(Long id, String note, String operator);

    /** 已改齐退回一步重来；上一回落下的一笔留底，不与后补之笔并排 */
    TBarrSiteCard revert(Long id, String note, String operator);

    /** 收口到头：此后加不进、字改不动、撤不下；同点位名额此时才空出 */
    TBarrSiteCard close(Long id, String note, String operator);

    /** 撤卡：只许撤在走卡，收口卡撤不下 */
    int deleteTBarrSiteCardByIds(String ids, String operator);

    /** 卡面三种摆法（街道/底册类别/进展）同一次回库计算，户数与总数接平 */
    CardSummary summary(SiteCardQuery query, List<String> visibleStreetCodes);

    /** 这张卡走过的每一笔（时间正序；回退旧笔仍在） */
    List<TBarrCardLog> listLogs(Long cardId);

    /** 当前登录人看得见的街道代号；无授权则空表（不借邻街） */
    List<String> visibleStreetCodes(Long userId);

    /** 本季码，如 2026Q4 */
    String currentSeason();
}
