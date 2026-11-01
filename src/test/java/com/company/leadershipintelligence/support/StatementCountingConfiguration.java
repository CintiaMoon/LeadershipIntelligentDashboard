package com.company.leadershipintelligence.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

/** Wraps the DataSource so tests can count the SQL statements a use case prepares (V3 Section 38.3 query-count checks). */
@TestConfiguration
public class StatementCountingConfiguration {

    public static final AtomicLong STATEMENTS = new AtomicLong();

    @Bean
    static BeanPostProcessor statementCounter() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource ds) || Proxy.isProxyClass(bean.getClass())) {
                    return bean;
                }
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(ds, args);
                        if (result instanceof Connection connection && method.getName().equals("getConnection")) {
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
                                if (m.getName().equals("prepareStatement") || m.getName().equals("createStatement")
                                        || m.getName().equals("prepareCall")) {
                                    STATEMENTS.incrementAndGet();
                                }
                                try {
                                    return m.invoke(connection, a);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
            }
        };
    }
}
