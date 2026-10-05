package com.fc.v2.controller.admin;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.SiteCardRuleException;
import com.fc.v2.common.log.Log;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TBarrSiteCard;
import com.fc.v2.model.custom.CardSummary;
import com.fc.v2.model.custom.SiteCardForm;
import com.fc.v2.model.custom.SiteCardQuery;
import com.fc.v2.service.ITBarrSiteCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 改造进度卡 Controller。
 * 一处一点位一张卡：卡代号、差口、进展档全由系统落；亲手立与当月验收带出走同一入口同一套数法。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "改造进度卡")
@Controller
@RequestMapping("/BarrSiteCardController")
public class BarrSiteCardController extends BaseController {

    private final String prefix = "admin/barrSiteCard";

    /** 部岗角色名：三层都看得见，但只看见划给它的那几条街 */
    private static final String ROLE_DEPT = "无障碍部岗";
    /** 街道岗角色名：只看本街挂的卡 */
    private static final String ROLE_STREET = "无障碍街道岗";

    @Autowired
    private ITBarrSiteCardService barrSiteCardService;

    @Autowired
    private TBarrArchMapper barrArchMapper;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrSiteCard:view")
    public String view(ModelMap model) {
        model.put("season", barrSiteCardService.currentSeason());
        model.put("deptRole", ShiroUtils.getSubjct() != null && ShiroUtils.getSubjct().hasRole(ROLE_DEPT));
        model.put("streetRole", ShiroUtils.getSubjct() != null && ShiroUtils.getSubjct().hasRole(ROLE_STREET));
        return prefix + "/list";
    }

    @Log(title = "改造进度卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrSiteCard:list")
    @ResponseBody
    public ResultTable list(SiteCardQuery query) {
        startPage();
        List<String> streets = barrSiteCardService.visibleStreetCodes(ShiroUtils.getUserId());
        PageInfo<TBarrSiteCard> page = new PageInfo<TBarrSiteCard>(
                barrSiteCardService.selectTBarrSiteCardList(barrSiteCardService.buildWrapper(query, streets)));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "改造进度卡三摆法汇总", action = "summary")
    @ApiOperation(value = "按街道/底册类别/进展一次算齐", notes = "三份摆法户数同一次计算，接得平")
    @GetMapping("/summary")
    @RequiresPermissions("barr:barrSiteCard:list")
    @ResponseBody
    public AjaxResult summary(SiteCardQuery query) {
        List<String> streets = barrSiteCardService.visibleStreetCodes(ShiroUtils.getUserId());
        CardSummary summary = barrSiteCardService.summary(query, streets);
        return AjaxResult.successData(200, summary);
    }

    @ApiOperation(value = "新增跳转", notes = "新增跳转；source=1 即从当月验收带出，与亲手立同一张表同一套数法")
    @GetMapping("/add")
    @RequiresPermissions("barr:barrSiteCard:add")
    public String add(SiteCardForm form, ModelMap modelMap) {
        modelMap.put("form", form);
        modelMap.put("archList", activeArchs());
        return prefix + "/add";
    }

    @Log(title = "改造进度卡新增", action = "add")
    @ApiOperation(value = "立卡", notes = "亲手立/当月验收带出同走此处")
    @PostMapping("/add")
    @RequiresPermissions("barr:barrSiteCard:add")
    @ResponseBody
    public AjaxResult addSave(SiteCardForm form) {
        try {
            TBarrSiteCard card = barrSiteCardService.createCard(form, ShiroUtils.getLoginName());
            return AjaxResult.success("立卡成功，卡代号：" + card.getCardNo());
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "修改跳转", notes = "修改跳转")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("barr:barrSiteCard:edit")
    public String edit(@PathVariable("id") Long id, ModelMap modelMap) {
        TBarrSiteCard card = barrSiteCardService.selectTBarrSiteCardById(id);
        List<TBarrArch> archs = activeArchs();
        if (card != null && card.getArchId() != null) {
            // 卡挂的底册即便已停用也要摆出来（旧卡照旧列、照旧算），不许让下拉误并到别的底册
            boolean present = archs.stream().anyMatch(a -> a.getId() != null && a.getId().equals(card.getArchId()));
            if (!present) {
                TBarrArch current = barrArchMapper.selectById(card.getArchId());
                if (current != null) {
                    archs.add(0, current);
                }
            }
        }
        if (card == null) {
            modelMap.put("notFound", true);
        }
        modelMap.put("card", card);
        modelMap.put("archList", archs);
        return prefix + "/edit";
    }

    @Log(title = "改造进度卡修改", action = "edit")
    @ApiOperation(value = "改口保存", notes = "改口保存")
    @PostMapping("/edit")
    @RequiresPermissions("barr:barrSiteCard:edit")
    @ResponseBody
    public AjaxResult editSave(SiteCardForm form) {
        try {
            barrSiteCardService.updateCard(form, ShiroUtils.getLoginName());
            return AjaxResult.success("改口已保存，差口已按眼下处数重轧");
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "改造进度卡推进", action = "advance")
    @ApiOperation(value = "走完一级", notes = "新录→已改齐/缺项")
    @PostMapping("/advance")
    @RequiresPermissions("barr:barrSiteCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String note) {
        try {
            barrSiteCardService.advance(id, note, ShiroUtils.getLoginName());
            return AjaxResult.success("已走完一级");
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "改造进度卡回退", action = "revert")
    @ApiOperation(value = "退回一步重来", notes = "已改齐→新录；旧笔留底")
    @PostMapping("/revert")
    @RequiresPermissions("barr:barrSiteCard:revert")
    @ResponseBody
    public AjaxResult revert(Long id, String note) {
        try {
            barrSiteCardService.revert(id, note, ShiroUtils.getLoginName());
            return AjaxResult.success("已退回一步重来，上回那笔留底");
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "改造进度卡收口", action = "close")
    @ApiOperation(value = "收口到头", notes = "此后加不进、改不动、撤不下")
    @PostMapping("/close")
    @RequiresPermissions("barr:barrSiteCard:close")
    @ResponseBody
    public AjaxResult close(Long id, String note) {
        try {
            barrSiteCardService.close(id, note, ShiroUtils.getLoginName());
            return AjaxResult.success("已收口到头，同点位名额已空出");
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "改造进度卡删除", action = "remove")
    @ApiOperation(value = "撤卡", notes = "只撤得下在走卡")
    @DeleteMapping("/remove")
    @RequiresPermissions("barr:barrSiteCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        try {
            return toAjax(barrSiteCardService.deleteTBarrSiteCardByIds(ids, ShiroUtils.getLoginName()));
        } catch (SiteCardRuleException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "流转留痕", notes = "一张卡走过的每一笔")
    @GetMapping("/logs/{id}")
    @RequiresPermissions("barr:barrSiteCard:list")
    @ResponseBody
    public AjaxResult logs(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, barrSiteCardService.listLogs(id));
    }

    @ApiOperation(value = "导出占位", notes = "避免框架默认导出路径空指针")
    @GetMapping("/noop")
    public void noop(HttpServletResponse response) {
        response.setStatus(204);
    }

    private List<TBarrArch> activeArchs() {
        return barrArchMapper.selectList(new QueryWrapper<TBarrArch>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .orderByAsc("arch_kind")
                .orderByAsc("arch_no"));
    }
}
