package com.orderplace.dealer;

import com.orderplace.common.web.CommonWebConfig;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Import(CommonWebConfig.class)
public class DealerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DealerServiceApplication.class, args);
    }
}
