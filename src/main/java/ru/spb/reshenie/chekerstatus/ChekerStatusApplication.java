package ru.spb.reshenie.chekerstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.config.nsi.NsiProperties;
import ru.spb.reshenie.chekerstatus.config.security.AppSecurityProperties;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({NsiProperties.class, GitLabProperties.class, AppSecurityProperties.class})
public class ChekerStatusApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChekerStatusApplication.class, args);
    }
}
