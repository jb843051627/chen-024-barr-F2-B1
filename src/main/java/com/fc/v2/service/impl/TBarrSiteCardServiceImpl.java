package com.fc.v2.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.SiteCardRuleException;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.mapper.auto.TBarrCardLogMapper;
import com.fc.v2.mapper.auto.TBarrSiteCardMapper;
import com.fc.v2.mapper.auto.TBarrUserStreetMapper;
import com.fc.v2.mapper.custom.BarrCardSeqDao;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TBarrCardLog;
import com.fc.v2.model.auto.TBarrSiteCard;
import com.fc.v2.model.auto.TBarrUserStreet;
import com.fc.v2.model.auto.TSysStreet;
import com.fc.v2.model.custom.BarrStreetRef;
import com.fc.v2.model.custom.CardSummary;
import com.fc.v2.model.custom.SiteCardForm;
import com.fc.v2.model.custom.SiteCardQuery;
import com.fc.v2.service.ITBarrSiteCardService;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.StringUtils;

/**
 * 改造进度卡 Service业务层处理。
 * 卡只从区里这一处起：卡代号系统排、差口系统轧、档由级数系统数出，底册不动卡不动。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TBarrSiteCardServiceImpl extends ServiceImpl<TBarrSiteCardMapper, TBarrSiteCard>
        implements ITBarrSiteCardService {

    /** 正整数（不许带零头、不许负数、不许空着） */
    private static final Pattern WHOLE_NUMBER = Pattern.compile("^\\d+$");

    @Autowired
    private TBarrArchMapper barrArchMapper;

    @Autowired
    private TBarrCardLogMapper barrCardLogMapper;

    @Autowired
    private TBarrUserStreetMapper barrUserStreetMapper;

    @Autowired
    private BarrCardSeqDao barrCardSeqDao;

    // ============================ 查询 ============================

    @Override
    public TBarrSiteCard selectTBarrSiteCardById(Long id) {
        TBarrSiteCard card = this.baseMapper.selectOne(new QueryWrapper<TBarrSiteCard>()
                .eq("id", id)
                .eq("del_flag", 0));
        return card == null ? null : normalize(card);
    }

    @Override
    public List<TBarrSiteCard> selectTBarrSiteCardList(Wrapper<TBarrSiteCard> queryWrapper) {
        List<TBarrSiteCard> list = this.baseMapper.selectList(queryWrapper);
        for (TBarrSiteCard card : list) {
            normalize(card);
        }
        return list;
    }

    @Override
    public Wrapper<TBarrSiteCard> buildWrapper(SiteCardQuery query, List<String> visibleStreetCodes) {
        // -3 已撤：库里立过的每一张卡都寻得着；默认只看未撤
        boolean includeRemoved = query != null && query.getStatus() != null && query.getStatus() == -3;
        QueryWrapper<TBarrSiteCard> wrapper = new QueryWrapper<TBarrSiteCard>()
                .eq("del_flag", includeRemoved ? 1 : 0);
        // 看不见的街一条都不进；没授权就是空，绝不借邻街
        if (visibleStreetCodes == null || visibleStreetCodes.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("street_code", visibleStreetCodes);
        }
        if (query != null) {
            if (StringUtils.isNotEmpty(query.getCardNo())) {
                wrapper.likeRight("card_no", query.getCardNo().trim());
            }
            if (StringUtils.isNotEmpty(query.getSiteNo())) {
                wrapper.likeRight("site_no", query.getSiteNo().trim());
            }
            if (StringUtils.isNotEmpty(query.getArchKind())) {
                wrapper.likeRight("arch_kind", query.getArchKind().trim());
            }
            if (StringUtils.isNotEmpty(query.getSeason())) {
                wrapper.eq("season", query.getSeason().trim());
            }
            if (query.getStatus() != null) {
                if (query.getStatus() == -1) {
                    wrapper.eq("closed", 0);
                } else if (query.getStatus() == -2) {
                    wrapper.eq("closed", 1);
                } else if (query.getStatus() >= 0) {
                    wrapper.eq("status", query.getStatus());
                }
            }
        }
        wrapper.orderByDesc("create_time");
        return wrapper;
    }

    @Override
    public List<String> visibleStreetCodes(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<TBarrUserStreet> rows = barrUserStreetMapper.selectList(
                new QueryWrapper<TBarrUserStreet>().eq("user_id", userId));
        List<String> codes = new ArrayList<>();
        for (TBarrUserStreet row : rows) {
            if (StringUtils.isNotEmpty(row.getStreetCode()) && !codes.contains(row.getStreetCode())) {
                codes.add(row.getStreetCode());
            }
        }
        return codes;
    }

    // ============================ 立卡 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrSiteCard createCard(SiteCardForm form, String operator) {
        if (form == null) {
            throw new SiteCardRuleException("卡面空着，收不进库");
        }
        String siteNo = trim(form.getSiteNo());
        if (StringUtils.isEmpty(siteNo)) {
            throw new SiteCardRuleException("点位或住户代号：空着，收不进库");
        }
        TBarrArch arch = loadArchForCreate(form.getArchId());
        int need = parseCount("应改处数", form.getNeedNum(), false);
        int done = parseCount("已改处数", form.getDoneNum(), true);
        if (done > need) {
            throw new SiteCardRuleException("已改处数：冒过了应改处数（已改" + done + "、应改" + need + "），收不进库");
        }

        String season = currentSeason();
        String cardNo = nextCardNo(season);

        TBarrSiteCard card = new TBarrSiteCard();
        card.setCardNo(cardNo);
        card.setSeason(season);
        card.setSiteNo(siteNo);
        card.setNeedNum(need);
        card.setDoneNum(done);
        card.setLackNum(need - done);
        card.setContent(trim(form.getContent()));
        card.setSource(form.getSource() != null && "1".equals(form.getSource().trim())
                ? TBarrSiteCard.SOURCE_ACCEPT : TBarrSiteCard.SOURCE_MANUAL);
        card.setStage(TBarrSiteCard.STAGE_NEW);
        card.setStatus(TBarrSiteCard.STATUS_NEW);
        card.setClosed(0);
        card.setDelFlag(0);
        card.setVerifyCode(MD5Util.encode(cardNo + "|" + season + "|" + siteNo));
        card.setRemark(trim(form.getRemark()));
        fillFromArch(card, arch);

        // 同点位同时只容一张在走卡（两家街同日各报、换人重报都不许叠）
        Integer active = this.baseMapper.selectCount(new QueryWrapper<TBarrSiteCard>()
                .eq("active_site", siteNo));
        if (active != null && active > 0) {
            throw new SiteCardRuleException("点位或住户代号「" + siteNo + "」已有一张在走卡，头一张未收口前第二张立不起来");
        }

        try {
            this.baseMapper.insert(card);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new SiteCardRuleException("点位或住户代号「" + siteNo + "」已有一张在走卡，一点位不许两卡叠着");
        }
        writeLog(card, TBarrCardLog.ACTION_CREATE, null, TBarrSiteCard.STAGE_NEW,
                "立卡（" + (card.getSource() == TBarrSiteCard.SOURCE_ACCEPT ? "当月验收带出" : "亲手立")
                        + "）：应改" + need + "、已改" + done + "、差" + (need - done), operator);
        return card;
    }

    // ============================ 改口编辑 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrSiteCard updateCard(SiteCardForm form, String operator) {
        if (form == null || StringUtils.isEmpty(form.getId())) {
            throw new SiteCardRuleException("不知道改口的是哪一张卡");
        }
        Long id;
        try {
            id = Long.valueOf(form.getId().trim());
        } catch (NumberFormatException e) {
            throw new SiteCardRuleException("卡代号不对，找不到这张卡");
        }
        TBarrSiteCard card = this.baseMapper.selectById(id);
        if (card == null || (card.getDelFlag() != null && card.getDelFlag() == 1)) {
            throw new SiteCardRuleException("这张卡找不着，或已经撤下");
        }
        normalize(card);
        touchLegacy(card, operator);
        if (card.getClosed() != null && card.getClosed() == 1) {
            throw new SiteCardRuleException("这张卡已收口到头：加不进去、字改不动、也撤不下来，非动不可只能另起新卡");
        }

        StringBuilder changed = new StringBuilder();

        // 挂靠底册：代号合不上底册的不许挂；停用底册不许新挂；底册全称以底册为准，随卡写法不一致改卡不改底
        if (StringUtils.isNotEmpty(form.getArchId()) && !form.getArchId().trim().equals(String.valueOf(card.getArchId()))) {
            TBarrArch target = loadArchForCreate(form.getArchId());
            fillFromArch(card, target);
            changed.append("挂靠底册改为").append(target.getArchNo()).append("；");
        } else if (card.getArchId() != null) {
            // 即便没换底册，全称/类别/街仍回头与底册对齐，屏上不许两样并存
            TBarrArch same = barrArchMapper.selectById(card.getArchId());
            if (same != null && (same.getDelFlag() == null || same.getDelFlag() == 0)) {
                fillFromArch(card, same);
            }
        }

        String siteNo = trim(form.getSiteNo());
        if (StringUtils.isEmpty(siteNo)) {
            throw new SiteCardRuleException("点位或住户代号：空着，收不进库");
        }
        if (!siteNo.equals(card.getSiteNo())) {
            Integer other = this.baseMapper.selectCount(new QueryWrapper<TBarrSiteCard>()
                    .eq("active_site", siteNo)
                    .ne("id", card.getId()));
            if (other != null && other > 0) {
                throw new SiteCardRuleException("点位或住户代号「" + siteNo + "」已有一张在走卡，不能把这张卡并过去");
            }
            card.setSiteNo(siteNo);
            changed.append("点位改为").append(siteNo).append("；");
        }

        int need = parseCount("应改处数", form.getNeedNum(), false);
        int done = parseCount("已改处数", form.getDoneNum(), true);
        if (done > need) {
            throw new SiteCardRuleException("已改处数：冒过了应改处数（已改" + done + "、应改" + need + "），收不进库");
        }
        if (card.getNeedNum() == null || need != card.getNeedNum()) {
            card.setNeedNum(need);
            changed.append("应改改为").append(need).append("；");
        }
        if (card.getDoneNum() == null || done != card.getDoneNum()) {
            card.setDoneNum(done);
            changed.append("已改改为").append(done).append("；");
        }

        // 差口三时机重轧：立卡、已改改口、换挂靠街。这里每次改口都按眼下应改/已改重轧一遍，新数盖旧数
        card.setLackNum(need - done);
        // 档仍由级数数出：屏上填的档、经办人记的档都不算数
        card.setStatus(deriveStatus(card.getStage(), need, done));

        card.setContent(trim(form.getContent()));
        card.setRemark(trim(form.getRemark()));

        // 眼下手这张不挡自己：唯一约束只认活卡占位列，更新时占位值不变即不撞
        try {
            this.baseMapper.updateById(card);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new SiteCardRuleException("点位或住户代号「" + siteNo + "」已有一张在走卡，一点位不许两卡叠着");
        }
        writeLog(card, TBarrCardLog.ACTION_EDIT, null, card.getStage(),
                changed.length() == 0 ? "改口编辑（处数未变，差口重轧）" : "改口编辑：" + changed, operator);
        return normalize(card);
    }

    // ============================ 推进 / 回退 / 收口 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrSiteCard advance(Long id, String note, String operator) {
        TBarrSiteCard card = loadAlive(id);
        touchLegacy(card, operator);
        int stage = nz(card.getStage());
        if (stage == TBarrSiteCard.STAGE_CLOSED) {
            throw new SiteCardRuleException("这张卡已收口到头，后面没有路可走");
        }
        if (stage >= TBarrSiteCard.STAGE_DONE) {
            throw new SiteCardRuleException("步子不能乱迈：已到已改齐这一级，改齐请走收口，还差处数先补齐");
        }
        int need = nz(card.getNeedNum());
        int done = nz(card.getDoneNum());
        card.setStage(TBarrSiteCard.STAGE_DONE);
        card.setStatus(deriveStatus(TBarrSiteCard.STAGE_DONE, need, done));
        this.baseMapper.updateById(card);
        writeLog(card, TBarrCardLog.ACTION_ADVANCE, stage, TBarrSiteCard.STAGE_DONE,
                "走完一级：新录 → " + (card.getStatus() == TBarrSiteCard.STATUS_DONE ? "已改齐" : "缺项")
                        + appendNote(note), operator);
        return normalize(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrSiteCard revert(Long id, String note, String operator) {
        TBarrSiteCard card = loadAlive(id);
        touchLegacy(card, operator);
        int stage = nz(card.getStage());
        if (stage == TBarrSiteCard.STAGE_CLOSED) {
            throw new SiteCardRuleException("已收口的卡退不回来，非动不可只能另起新卡");
        }
        if (stage == TBarrSiteCard.STAGE_NEW) {
            throw new SiteCardRuleException("还在新录这一级，前面没有可退回的步");
        }
        // 上一回落下的那笔（advance 留痕）依旧还在，这里另起一笔，不并排、不抹旧笔
        card.setStage(TBarrSiteCard.STAGE_NEW);
        card.setStatus(TBarrSiteCard.STATUS_NEW);
        this.baseMapper.updateById(card);
        writeLog(card, TBarrCardLog.ACTION_REVERT, stage, TBarrSiteCard.STAGE_NEW,
                "已改齐退回一步重来，回新录重办" + appendNote(note), operator);
        return normalize(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrSiteCard close(Long id, String note, String operator) {
        TBarrSiteCard card = loadAlive(id);
        touchLegacy(card, operator);
        int stage = nz(card.getStage());
        if (stage == TBarrSiteCard.STAGE_CLOSED) {
            throw new SiteCardRuleException("这张卡已经收口，不必再收");
        }
        if (stage < TBarrSiteCard.STAGE_DONE) {
            throw new SiteCardRuleException("步子不能乱迈：已改齐这一级还没走完，收口这条路不给走");
        }
        int need = nz(card.getNeedNum());
        int done = nz(card.getDoneNum());
        if (done < need) {
            throw new SiteCardRuleException("还差 " + (need - done) + " 处，缺项卡收不了口；补齐改齐后再来");
        }
        card.setStage(TBarrSiteCard.STAGE_CLOSED);
        card.setClosed(1);
        card.setStatus(TBarrSiteCard.STATUS_DONE);
        this.baseMapper.updateById(card);
        // 收口后 active_site 占位列自动落空，同点位才接得上新卡
        writeLog(card, TBarrCardLog.ACTION_CLOSE, stage, TBarrSiteCard.STAGE_CLOSED,
                "收口到头：此后加不进、改不动、撤不下" + appendNote(note), operator);
        return normalize(card);
    }

    // ============================ 撤卡 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTBarrSiteCardByIds(String ids, String operator) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        int rows = 0;
        for (Long id : idArr) {
            TBarrSiteCard card = this.baseMapper.selectById(id);
            if (card == null || (card.getDelFlag() != null && card.getDelFlag() == 1)) {
                continue;
            }
            normalize(card);
            if (card.getClosed() != null && card.getClosed() == 1) {
                throw new SiteCardRuleException("卡「" + card.getCardNo() + "」已收口到头，撤不下来");
            }
            card.setDelFlag(1);
            rows += this.baseMapper.updateById(card);
        }
        return rows;
    }

    // ============================ 三份摆法 ============================

    @Override
    public CardSummary summary(SiteCardQuery query, List<String> visibleStreetCodes) {
        CardSummary summary = new CardSummary();
        Map<String, Long> byStreet = new LinkedHashMap<>();
        Map<String, Long> byArchKind = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("新录", 0L);
        byStatus.put("已改齐", 0L);
        byStatus.put("缺项", 0L);

        List<TBarrSiteCard> all;
        if (visibleStreetCodes == null || visibleStreetCodes.isEmpty()) {
            // 空着就是空着，不从邻街借行
            all = new ArrayList<>();
        } else {
            all = this.baseMapper.selectList(buildWrapper(query, visibleStreetCodes));
        }
        for (TBarrSiteCard card : all) {
            normalize(card);
            String street = StringUtils.isNotEmpty(card.getStreetName()) ? card.getStreetName() : card.getStreetCode();
            byStreet.merge(street == null ? "未落街" : street, 1L, Long::sum);
            byArchKind.merge(card.getArchKind() == null ? "未分类" : card.getArchKind(), 1L, Long::sum);
            byStatus.merge(statusName(card.getStatus()), 1L, Long::sum);
        }
        summary.setTotal(all.size());
        summary.setByStreet(toGroups(byStreet));
        summary.setByArchKind(toGroups(byArchKind));
        summary.setByStatus(toGroups(byStatus));
        return summary;
    }

    @Override
    public List<TBarrCardLog> listLogs(Long cardId) {
        return barrCardLogMapper.selectList(new QueryWrapper<TBarrCardLog>()
                .eq("card_id", cardId)
                .orderByAsc("create_time")
                .orderByAsc("id"));
    }

    @Override
    public String currentSeason() {
        LocalDate now = LocalDate.now();
        int q = (now.getMonthValue() - 1) / 3 + 1;
        return now.getYear() + "Q" + q;
    }

    // ============================ 内部方法 ============================

    /** 旧卡接住：步数/卡号/核验串空着不报错，头一回动它时补齐，在走的不当成已收口 */
    private void touchLegacy(TBarrSiteCard card, String operator) {
        boolean changed = false;
        if (card.getClosed() == null) {
            card.setClosed(0);
            changed = true;
        }
        if (card.getStage() == null) {
            // 旧档反推级数：已改齐/缺项都算走到第二级；新录或空值算一级未走（绝不按已收口）
            card.setStage(card.getStatus() != null && card.getStatus() >= TBarrSiteCard.STATUS_DONE
                    ? TBarrSiteCard.STAGE_DONE : TBarrSiteCard.STAGE_NEW);
            changed = true;
        }
        if (card.getSource() == null) {
            card.setSource(TBarrSiteCard.SOURCE_MANUAL);
            changed = true;
        }
        if (card.getDelFlag() == null) {
            card.setDelFlag(0);
            changed = true;
        }
        if (StringUtils.isEmpty(card.getSeason())) {
            card.setSeason(currentSeason());
            changed = true;
        }
        if (StringUtils.isEmpty(card.getCardNo())) {
            card.setCardNo(nextCardNo(card.getSeason()));
            changed = true;
        }
        if (StringUtils.isEmpty(card.getVerifyCode())) {
            card.setVerifyCode(MD5Util.encode(card.getCardNo() + "|" + card.getSeason() + "|" + card.getSiteNo()));
            changed = true;
        }
        if (card.getNeedNum() != null) {
            int lack = card.getNeedNum() - nz(card.getDoneNum());
            if (card.getLackNum() == null || card.getLackNum() != lack) {
                card.setLackNum(lack);
                changed = true;
            }
            // 档由级数与处数重数一遍：旧卡手记的档若与眼下数不符，以系统数出的为准
            card.setStatus(deriveStatus(card.getStage(), card.getNeedNum(), nz(card.getDoneNum())));
            changed = true;
        } else if (card.getStatus() == null) {
            card.setStatus(TBarrSiteCard.STATUS_NEW);
            changed = true;
        }
        if (changed) {
            this.baseMapper.updateById(card);
        }
    }

    /** 只用于回显：空字段按"在走"接住，不碰库；旧档反推级数，在走的绝不当成已收口 */
    private TBarrSiteCard normalize(TBarrSiteCard card) {
        if (card.getClosed() == null) {
            card.setClosed(0);
        }
        if (card.getStage() == null) {
            card.setStage(card.getStatus() != null && card.getStatus() >= TBarrSiteCard.STATUS_DONE
                    ? TBarrSiteCard.STAGE_DONE : TBarrSiteCard.STAGE_NEW);
        }
        if (card.getSource() == null) {
            card.setSource(TBarrSiteCard.SOURCE_MANUAL);
        }
        if (card.getNeedNum() != null) {
            card.setLackNum(card.getNeedNum() - nz(card.getDoneNum()));
            card.setStatus(deriveStatus(card.getStage(), card.getNeedNum(), nz(card.getDoneNum())));
        } else if (card.getStatus() == null) {
            card.setStatus(TBarrSiteCard.STATUS_NEW);
        }
        return card;
    }

    private TBarrSiteCard loadAlive(Long id) {
        if (id == null) {
            throw new SiteCardRuleException("不知道动的是哪一张卡");
        }
        TBarrSiteCard card = this.baseMapper.selectOne(new QueryWrapper<TBarrSiteCard>()
                .eq("id", id).eq("del_flag", 0));
        if (card == null) {
            throw new SiteCardRuleException("这张卡找不着，或已经撤下");
        }
        return normalize(card);
    }

    private TBarrArch loadArchForCreate(String archIdText) {
        if (StringUtils.isEmpty(archIdText)) {
            throw new SiteCardRuleException("挂在哪一处底册名下：空着，没有底册卡起不来");
        }
        Long archId;
        try {
            archId = Long.valueOf(archIdText.trim());
        } catch (NumberFormatException e) {
            throw new SiteCardRuleException("底册代号合不上底册，卡起不来");
        }
        TBarrArch arch = barrArchMapper.selectOne(new QueryWrapper<TBarrArch>()
                .eq("id", archId).eq("del_flag", 0));
        if (arch == null) {
            throw new SiteCardRuleException("底册代号合不上在册底册，卡起不来");
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            throw new SiteCardRuleException("底册「" + arch.getArchNo() + "」已停用，停用之后不能再往它名下立新卡");
        }
        return arch;
    }

    /** 卡上底册代号/全称/类别/街一律以底册为准：改卡不改底，屏上不许两样并存 */
    private void fillFromArch(TBarrSiteCard card, TBarrArch arch) {
        card.setArchId(arch.getId());
        card.setArchNo(arch.getArchNo());
        card.setArchName(arch.getArchName());
        card.setArchKind(arch.getArchKind());
        BarrStreetRef ref = resolveStreet(arch.getRoadName());
        card.setStreetCode(ref.getStreetCode());
        card.setStreetName(ref.getStreetName());
    }

    /** 属地一路写到街道，最后一段即街名，回头去街道册里对代号；对不上不许立卡 */
    private BarrStreetRef resolveStreet(String roadName) {
        if (StringUtils.isEmpty(roadName)) {
            throw new SiteCardRuleException("底册属地没有写到街道，卡挂不上街，收不进库");
        }
        String tail = roadName;
        int idx = Math.max(roadName.lastIndexOf('—'), roadName.lastIndexOf('-'));
        if (idx >= 0 && idx < roadName.length() - 1) {
            tail = roadName.substring(idx + 1);
        }
        tail = tail.trim();
        TSysStreet street = streetMapperSafe(tail);
        if (street == null) {
            throw new SiteCardRuleException("底册属地「" + tail + "」在街道册里对不上，卡挂不上街，收不进库");
        }
        return new BarrStreetRef(street.getStreetCode(), street.getStreetName());
    }

    @Autowired(required = false)
    private com.fc.v2.mapper.auto.TSysStreetMapper sysStreetMapper;

    private TSysStreet streetMapperSafe(String streetName) {
        if (sysStreetMapper == null) {
            return null;
        }
        List<TSysStreet> list = sysStreetMapper.selectList(new QueryWrapper<TSysStreet>()
                .and(w -> w.isNull("data_state").or().ne("data_state", 1))
                .eq("street_name", streetName)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 卡代号系统排：一季一排，本子自带号递来一概不认 */
    private synchronized String nextCardNo(String season) {
        barrCardSeqDao.ensureSeason(season);
        barrCardSeqDao.bump(season);
        long seq = barrCardSeqDao.currentValue();
        return String.format("K%s-%06d", season, seq);
    }

    private void writeLog(TBarrSiteCard card, String action, Integer fromStage, Integer toStage,
                          String note, String operator) {
        TBarrCardLog log = new TBarrCardLog();
        log.setCardId(card.getId());
        log.setCardNo(card.getCardNo());
        log.setAction(action);
        log.setFromStage(fromStage);
        log.setToStage(toStage);
        log.setStepNote(note);
        log.setOperator(operator);
        log.setCreateTime(new java.util.Date());
        barrCardLogMapper.insert(log);
    }

    /** 档由级数与处数自行数出：0 级新录；1 级看差口分已改齐/缺项；收口仍是已改齐 */
    private int deriveStatus(int stage, int need, int done) {
        if (stage <= TBarrSiteCard.STAGE_NEW) {
            return TBarrSiteCard.STATUS_NEW;
        }
        return done >= need ? TBarrSiteCard.STATUS_DONE : TBarrSiteCard.STATUS_LACK;
    }

    private int parseCount(String field, String raw, boolean blankAsZero) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            if (blankAsZero) {
                return 0;
            }
            throw new SiteCardRuleException(field + "：空着，收不进库");
        }
        v = v.replaceFirst("^[+]", "");
        if (v.startsWith("-")) {
            throw new SiteCardRuleException(field + "：不许是负数（" + raw.trim() + "），收不进库");
        }
        if (v.contains(".") || v.contains("．") || v.contains("、")) {
            throw new SiteCardRuleException(field + "：带零头（" + raw.trim() + "），处数只收整数，收不进库");
        }
        if (!WHOLE_NUMBER.matcher(v).matches()) {
            throw new SiteCardRuleException(field + "：不是整数（" + raw.trim() + "），收不进库");
        }
        try {
            long n = Long.parseLong(v);
            if (n > Integer.MAX_VALUE) {
                throw new SiteCardRuleException(field + "：数太大，收不进库");
            }
            return (int) n;
        } catch (NumberFormatException e) {
            throw new SiteCardRuleException(field + "：不是整数（" + raw.trim() + "），收不进库");
        }
    }

    private List<CardSummary.GroupCount> toGroups(Map<String, Long> map) {
        List<CardSummary.GroupCount> groups = new ArrayList<>();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            groups.add(new CardSummary.GroupCount(e.getKey(), e.getValue()));
        }
        return groups;
    }

    private String statusName(Integer status) {
        if (status != null && status == TBarrSiteCard.STATUS_DONE) {
            return "已改齐";
        }
        if (status != null && status == TBarrSiteCard.STATUS_LACK) {
            return "缺项";
        }
        return "新录";
    }

    private String appendNote(String note) {
        String n = trim(note);
        return StringUtils.isEmpty(n) ? "" : "；" + n;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
