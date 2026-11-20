package com.sistema.sistema_riego_inteligente.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host}")
    private String mailtrapHost;
    @Value("${spring.mail.port}")
    private int mailtrapPort;
    @Value("${spring.mail.username}")
    private String mailtrapUser;
    @Value("${spring.mail.password}")
    private String mailtrapPass;

    @Value("${app.mail.real.host}")
    private String realHost;
    @Value("${app.mail.real.port}")
    private int realPort;
    @Value("${app.mail.real.username}")
    private String realUser;
    @Value("${app.mail.real.password}")
    private String realPass;

    @Bean
    @Primary
    public JavaMailSender mailtrapMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailtrapHost);
        sender.setPort(mailtrapPort);
        sender.setUsername(mailtrapUser);
        sender.setPassword(mailtrapPass);
        sender.setJavaMailProperties(smtpProps());
        return sender;
    }

    @Bean("realMailSender")
    public JavaMailSender realMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(realHost);
        sender.setPort(realPort);
        sender.setUsername(realUser);
        sender.setPassword(realPass);
        sender.setJavaMailProperties(smtpProps());
        return sender;
    }

    private Properties smtpProps() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return props;
    }
}