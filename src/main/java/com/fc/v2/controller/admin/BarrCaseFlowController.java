package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.ITBarrCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 一户一档改造卷宗 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "一户一档改造卷宗")
@Controller
@RequestMapping("/barrCaseFlow")
public class BarrCaseFlowController extends BaseController {

    private final String prefix = "admin/barrCaseFlow";

    @Autowired
    private ITBarrCaseFlowService barrCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("barrCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "一户一档改造卷宗流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("barrCaseFlow:list")
    @ResponseBody
    public ResultTable list(TBarrCaseFlow record) {
        QueryWrapper<TBarrCaseFlow> queryWrapper = new QueryWrapper<TBarrCaseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TBarrCaseFlow> page =
                new com.github.pagehelper.PageInfo<TBarrCaseFlow>(barrCaseFlowService.selectTBarrCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "一户一档改造卷宗推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("barrCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(barrCaseFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "一户一档改造卷宗回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("barrCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(barrCaseFlowService.rollback(id, remark) != null ? 1 : 0);
    }
}
