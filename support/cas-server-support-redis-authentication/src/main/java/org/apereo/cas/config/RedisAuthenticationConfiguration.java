package org.apereo.cas.config;

import org.apereo.cas.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalFactoryUtils;
import org.apereo.cas.authentication.principal.PrincipalNameTransformerUtils;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.authentication.support.password.PasswordEncoderUtils;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.persondir.PersonDirectoryAttributeRepositoryPlan;
import org.apereo.cas.persondir.PersonDirectoryAttributeRepositoryPlanConfigurer;
import org.apereo.cas.redis.RedisAuthenticationHandler;
import org.apereo.cas.redis.RedisPersonAttributeDao;
import org.apereo.cas.redis.core.RedisObjectFactory;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.util.function.FunctionUtils;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This is {@link RedisAuthenticationConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Configuration("redisAuthenticationConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class RedisAuthenticationConfiguration {
    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("servicesManager")
    private ObjectProvider<ServicesManager> servicesManager;

    @Autowired
    @Qualifier("defaultPrincipalResolver")
    private ObjectProvider<PrincipalResolver> defaultPrincipalResolver;

    @Bean
    public PrincipalFactory redisPrincipalFactory() {
        return PrincipalFactoryUtils.newPrincipalFactory();
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisAuthenticationConnectionFactory")
    public RedisConnectionFactory redisAuthenticationConnectionFactory() {
        val redis = casProperties.getAuthn().getRedis();
        return RedisObjectFactory.newRedisConnectionFactory(redis);
    }

    @Bean(name = {"authenticationRedisTemplate", "redisTemplate"})
    @ConditionalOnMissingBean(name = "authenticationRedisTemplate")
    public RedisTemplate authenticationRedisTemplate() {
        return RedisObjectFactory.newRedisTemplate(redisAuthenticationConnectionFactory());
    }

    @Bean
    public AuthenticationHandler redisAuthenticationHandler() {
        val redis = casProperties.getAuthn().getRedis();
        val handler = new RedisAuthenticationHandler(redis.getName(),
            servicesManager.getIfAvailable(),
            redisPrincipalFactory(), redis.getOrder(),
            authenticationRedisTemplate());

        handler.setPrincipalNameTransformer(PrincipalNameTransformerUtils.newPrincipalNameTransformer(redis.getPrincipalTransformation()));
        handler.setPasswordEncoder(PasswordEncoderUtils.newPasswordEncoder(redis.getPasswordEncoder()));
        return handler;
    }

    @ConditionalOnMissingBean(name = "redisAuthenticationEventExecutionPlanConfigurer")
    @Bean
    public AuthenticationEventExecutionPlanConfigurer redisAuthenticationEventExecutionPlanConfigurer() {
        return plan ->
            plan.registerAuthenticationHandlerWithPrincipalResolver(redisAuthenticationHandler(), defaultPrincipalResolver.getIfAvailable());
    }

    @ConditionalOnMissingBean(name = "redisPersonAttributeDaos")
    @Bean
    public List<IPersonAttributeDao> redisPersonAttributeDaos() {
        val redis = casProperties.getAuthn().getAttributeRepository().getRedis();
        return redis
            .stream()
            .filter(r -> StringUtils.isNotBlank(r.getHost()))
            .map(r -> {
                val conn = RedisObjectFactory.newRedisConnectionFactory(r, true);
                val template = RedisObjectFactory.newRedisTemplate(conn);
                template.afterPropertiesSet();
                val cb = new RedisPersonAttributeDao(template);
                cb.setOrder(r.getOrder());
                FunctionUtils.doIfNotNull(r.getId(), cb::setId);
                return cb;
            })
            .collect(Collectors.toList());
    }

    @ConditionalOnMissingBean(name = "redisAttributeRepositoryPlanConfigurer")
    @Bean
    public PersonDirectoryAttributeRepositoryPlanConfigurer redisAttributeRepositoryPlanConfigurer() {
        return new PersonDirectoryAttributeRepositoryPlanConfigurer() {
            @Override
            public void configureAttributeRepositoryPlan(final PersonDirectoryAttributeRepositoryPlan plan) {
                val daos = redisPersonAttributeDaos();
                daos.forEach(plan::registerAttributeRepository);
            }
        };
    }
}
