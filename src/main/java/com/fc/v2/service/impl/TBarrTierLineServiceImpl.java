package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrTierLineMapper;
import com.fc.v2.model.auto.TBarrTierLine;
import com.fc.v2.model.custom.TierVerdict;
import com.fc.v2.service.ITBarrTierLineService;

/**
 * 入户评估定档线 Service业务层处理（rule-eval 形状）。
 * 四问唯一出处：那一刻哪条线说话、请不出线给不给说法、并列两条听谁的、压线往哪头落，
 * 全在下面这条求值走法里；接口不代判，屏面不预设，报表不另立口径。
 * 时段比较全在 Java 里走（册子小，整册捞出再逐条问）：
 * JDBC 的 serverTimezone 与本机时区不对称，把 Date 绑进 SQL 会整体偏移，
 * 边界判定在"切换当日"这类用例上随机错档（实测踩到），干脆不让 SQL 碰时段。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrTierLineServiceImpl implements ITBarrTierLineService {

    /** 评估分满分一百 */
    private static final BigDecimal SCORE_MAX = new BigDecimal("100");

    /** 取齐的小数位：与册子 decimal(12,3) 同一把尺 */
    private static final int SCORE_SCALE = 3;

    /** 交班次序高的先说；序一样高比线代号，照排法排在后面的赢；再齐平比主键——同一刻只容一个结论 */
    private static final Comparator<TBarrTierLine> PRIORITY_THEN_CODE_DESC = new Comparator<TBarrTierLine>() {
        @Override
        public int compare(TBarrTierLine a, TBarrTierLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ib, ia);
        }
    };

    @javax.annotation.Resource
    private TBarrTierLineMapper barrTierLineMapper;

    @Override
    public TBarrTierLine selectTBarrTierLineById(Long id) {
        return this.barrTierLineMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        // 说话的是问话那一刻在位的那一条：同一代号几版并立时，仍按同一套先后挑一条
        TBarrTierLine line = null;
        for (TBarrTierLine r : registry()) {
            if (ruleCode.equals(r.getRuleCode()) && inService(r, at)
                    && (line == null || PRIORITY_THEN_CODE_DESC.compare(r, line) < 0)) {
                line = r;
            }
        }
        return verdictOf(line, input).getTier();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return evaluateVerdict(input, at).getTier();
    }

    @Override
    public TierVerdict evaluateVerdict(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return TierVerdict.noLine();
        }
        List<TBarrTierLine> avail = listAvailable(at);
        return verdictOf(avail.isEmpty() ? null : avail.get(0), input);
    }

    @Override
    public List<TBarrTierLine> listAvailable(Date at) {
        List<TBarrTierLine> out = new ArrayList<TBarrTierLine>();
        if (at == null) {
            return out;
        }
        for (TBarrTierLine r : registry()) {
            if (inService(r, at)) {
                out.add(r);
            }
        }
        out.sort(PRIORITY_THEN_CODE_DESC);
        return out;
    }

    @Override
    public List<String> listDanglingRuleCodes(Date at) {
        List<String> out = new ArrayList<String>();
        if (at == null) {
            return out;
        }
        Set<String> live = new HashSet<String>();
        for (TBarrTierLine r : listAvailable(at)) {
            if (r.getRuleCode() != null) {
                live.add(r.getRuleCode());
            }
        }
        for (TBarrTierLine r : registry()) {
            String code = r.getRuleCode();
            if (code != null && !code.trim().isEmpty() && !live.contains(code) && !out.contains(code)) {
                out.add(code);
            }
        }
        Collections.sort(out);
        return out;
    }

    @Override
    public String boundaryProblem(TBarrTierLine line) {
        if (line == null) {
            return "线本体空着，这条线用不了";
        }
        if (line.getTh1Max() == null) {
            return "起算分空着，这条线用不了";
        }
        if (line.getTh2Max() == null) {
            return "够线分空着，这条线用不了";
        }
        if (line.getTh3Max() == null) {
            return "封顶分空着，这条线用不了";
        }
        if (line.getTh2Max().compareTo(line.getTh1Max()) <= 0) {
            return "够线分没能压过起算分（起算" + line.getTh1Max() + "、够线" + line.getTh2Max() + "），这条线用不了";
        }
        if (line.getTh3Max().compareTo(line.getTh2Max()) <= 0) {
            return "封顶分没能压过够线分（够线" + line.getTh2Max() + "、封顶" + line.getTh3Max() + "），这条线用不了";
        }
        return null;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TBarrTierLine r = this.barrTierLineMapper.selectById(id);
        return r != null && inService(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    // ============================ 内部：四问唯一出处 ============================

    /** 整册捞出（未删的都在，含已卸下/未起用：屏上列得出，只是不一定请得动） */
    private List<TBarrTierLine> registry() {
        return this.barrTierLineMapper.selectList(new QueryWrapper<TBarrTierLine>().eq("del_flag", 0));
    }

    /**
     * 那一刻在不在位：起用那一日已经够着、交班那一日还没走到（交班当日不含）；
     * 交班一栏空着按一直在位认（早年手抄进册的线，兼容守住）；
     * 已卸下的、三个分界数立不住的，一律请不动。
     */
    private boolean inService(TBarrTierLine r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() == 1) {
            return false;
        }
        if (r.getStatus() != null && r.getStatus() == 1) {
            return false;
        }
        if (r.getEffStart() == null || r.getEffStart().after(at)) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return boundaryProblem(r) == null;
    }

    /**
     * 一条线对一个分下一趟结论：先按同一把舍法取齐，再拿取齐的数去比线。
     * 取数只此一处，比数不再二次取舍——去年被生生挪低一档的那户，差就差在取数与比数分了家。
     */
    private TierVerdict verdictOf(TBarrTierLine line, BigDecimal input) {
        if (line == null || input == null) {
            return TierVerdict.noLine();
        }
        BigDecimal score = align(input);
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(SCORE_MAX) > 0) {
            return TierVerdict.noLine();
        }
        int tier = tierOf(line, score);
        if (tier == TierVerdict.TIER_NONE) {
            return TierVerdict.noLine();
        }
        return TierVerdict.of(tier, line.getRuleCode(), line.getEffStart());
    }

    /** 同一把舍法：评估分一律先按 3 位小数四舍五入取齐，全系统只此一把 */
    private BigDecimal align(BigDecimal input) {
        return input.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** 压线往高档走：够上封顶即重点，够上够线即提升，够上起算即基础；一条都够不上不给档 */
    private int tierOf(TBarrTierLine line, BigDecimal score) {
        if (score.compareTo(line.getTh3Max()) >= 0) {
            return TierVerdict.TIER_KEY;
        }
        if (score.compareTo(line.getTh2Max()) >= 0) {
            return TierVerdict.TIER_LIFT;
        }
        if (score.compareTo(line.getTh1Max()) >= 0) {
            return TierVerdict.TIER_BASIC;
        }
        return TierVerdict.TIER_NONE;
    }
}
