package com.fc.v2.common.exception;

/**
 * 改造进度卡业务规则异常。
 * 打回去必须讲清毛病落在哪一栏（如"应改处数：空着"），不许只丢一句"数不对劲"。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class SiteCardRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SiteCardRuleException(String message) {
        super(message);
    }
}
