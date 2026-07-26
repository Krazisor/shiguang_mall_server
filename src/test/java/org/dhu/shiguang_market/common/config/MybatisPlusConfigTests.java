package org.dhu.shiguang_market.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

class MybatisPlusConfigTests {

    @Test
    void registersOptimisticLockBeforeBoundedMysqlPagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).hasSize(2);
        assertThat(interceptor.getInterceptors().get(0))
                .isInstanceOf(OptimisticLockerInnerInterceptor.class);
        assertThat(interceptor.getInterceptors().get(1))
                .isInstanceOf(PaginationInnerInterceptor.class);

        PaginationInnerInterceptor pagination =
                (PaginationInnerInterceptor) interceptor.getInterceptors().get(1);
        assertThat(pagination.isOverflow()).isFalse();
        assertThat(pagination.getMaxLimit()).isEqualTo(MybatisPlusConfig.MAX_PAGE_SIZE);
    }
}
