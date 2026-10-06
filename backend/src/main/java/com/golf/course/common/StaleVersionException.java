package com.golf.course.common;

import com.golf.course.dto.ScorecardView;

/**
 * 页面基于过期版本操作时抛出：返回 409、当前记分卡状态，并要求页面刷新。
 */
public class StaleVersionException extends BizException {

    private final ScorecardView current;

    public StaleVersionException(String message, ScorecardView current) {
        super(message);
        this.current = current;
    }

    public ScorecardView getCurrent() {
        return current;
    }
}
