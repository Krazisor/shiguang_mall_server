package org.dhu.shiguang_market;

import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "org.dhu.shiguang_market", markerInterface = com.baomidou.mybatisplus.core.mapper.BaseMapper.class)
public class ShiguangMarketApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(ShiguangMarketApplication.class, args);
    }

}
