package no.nav.dokgen.configuration;

import no.nav.dokgen.log.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@ComponentScan({"no.nav.dokgen"})
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    public FilterRegistrationBean<no.nav.dokgen.log.LogFilter> logFilter() {
        log.info("Registration of the LogFilter");
        final FilterRegistrationBean<LogFilter> filterRegistration = new FilterRegistrationBean<>();
        filterRegistration.setFilter(new LogFilter());
        filterRegistration.setOrder(1);
        return filterRegistration;
    }
}
