package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TBarrTierLine;
import com.fc.v2.model.custom.TierVerdict;

/**
 * 入户评估定档线 Service接口（rule-eval 形状：规则求值，无增删改）。
 * 四问——某一刻该按哪条线归层、请不出线给不给说法、交班次序相同的两条听谁的、
 * 评估分正好压线时往哪一头落——只有业务服务层那一条求值方法一个出处：
 * 接口不代判，屏面不预设答案，报表脚本也不许另拿一套口径去凑。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBarrTierLineService {

    /** 按主键查询规则（册里翻得着，含已卸下：卸下只是不许捞它定档，不是查不着） */
    TBarrTierLine selectTBarrTierLineById(Long id);

    /**
     * 按线代号取值的老方法（原地不动：收什么给什么、回什么还是什么）。
     * 按 ruleCode 在 at 时刻在位的那一条判档（1基础 2提升 3重点）。
     * 无在位版本 / 输入越界 / 参数缺失一律返回 0（无可用线）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻在位线中交班次序最高者的档位；无可用线返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 整份结论求值：与 evaluateTop 同一趟走法，多带回钉进户卡的一对
     * （线代号、起用那一日，与这一趟求值当下回的完全对得住）。
     * 不给档时结论栏落"无可用线"，线代号与起用那一日两栏一并留空。
     */
    TierVerdict evaluateVerdict(BigDecimal input, Date at);

    /** at 时刻在位可用线名单（交班次序降序、线代号降序）；屏上那份与逐条翻册那份同一出处 */
    List<TBarrTierLine> listAvailable(Date at);

    /**
     * 屏上挂着线代号、at 时刻册里却请不出在位线的单子（一条条指出：
     * 未起用的、已交班的、已卸下的、三个分界数立不住的，代号都在此列）。
     */
    List<String> listDanglingRuleCodes(Date at);

    /**
     * 一条线的三个分界数能不能用：能用回 null；
     * 用不了讲清是哪一格空着、又是哪一格没能压过它前头那一格，不只丢一句"数不对劲"。
     */
    String boundaryProblem(TBarrTierLine line);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径与求值/名单一致） */
    boolean usable(Long id, Date at);

    /** at 时刻在位可用线条数（列表页角标 / 看板），与名单同一出处 */
    int countAvailable(Date at);
}
