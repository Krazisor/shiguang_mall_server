package org.dhu.shiguang_market.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageView<T>(List<T> items, long page, long pageSize, long total, long totalPages) {

    public static <T> PageView<T> of(IPage<?> page, List<T> items) {
        return new PageView<>(items, page.getCurrent(), page.getSize(), page.getTotal(), page.getPages());
    }
}
