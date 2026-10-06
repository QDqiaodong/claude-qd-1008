package com.golf.course.dto;

import java.util.List;

public record CloseCheckView(
        boolean canClose,
        List<String> reasons
) {
}
