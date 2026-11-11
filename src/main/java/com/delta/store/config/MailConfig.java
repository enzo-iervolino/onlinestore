package com.delta.store.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host:${MAIL_HOST:smtp.gmail.com}}")
    private String host;

    @Value("${spring.mail.port:${MAIL_PORT:587}}")
    private String port;

    @Value("${spring.mail.username:${MAIL_USERNAME:}}")
    private String username;

    @Value("${spring.mail.password:${MAIL_PASSWORD:}}")
    private String password;

    @Value("${spring.mail.default-encoding:UTF-8}")
    private String defaultEncoding;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private String smtpAuth;

    @Value("${MAIL_SSL_ENABLE:false}")
    private String sslEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String startTlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private String startTlsRequired;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:10000}")
    private String connectionTimeout;

    @Value("${spring.mail.properties.mail.smtp.timeout:10000}")
    private String timeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}")
    private String writeTimeout;

    @Value("${spring.mail.properties.mail.smtp.ssl.trust:}")
    private String sslTrust;

    @Value("${spring.mail.properties.mail.debug:false}")
    private String mailDebug;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cleanValue(host));
        sender.setPort(parseInt(cleanValue(port), 587));
        sender.setUsername(cleanValue(username));
        sender.setPassword(cleanSecret(password));
        sender.setDefaultEncoding(cleanValue(defaultEncoding, "UTF-8"));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", cleanValue(smtpAuth, "true"));
        props.put("mail.smtp.ssl.enable", cleanValue(sslEnable, "false"));
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.starttls.enable", cleanValue(startTlsEnable, "true"));
        props.put("mail.smtp.starttls.required", cleanValue(startTlsRequired, "true"));
        props.put("mail.smtp.connectiontimeout", cleanValue(connectionTimeout, "10000"));
        props.put("mail.smtp.timeout", cleanValue(timeout, "10000"));
        props.put("mail.smtp.writetimeout", cleanValue(writeTimeout, "10000"));
        props.put("mail.debug", cleanValue(mailDebug, "false"));

        String trust = cleanValue(sslTrust);
        if (!trust.isBlank()) {
            props.put("mail.smtp.ssl.trust", trust);
        }

        return sender;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String cleanValue(String value) {
        return cleanValue(value, "");
    }

    private String cleanValue(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String cleaned = value.trim();
        cleaned = stripWrappingQuotes(cleaned).trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String cleanSecret(String value) {
        String cleaned = cleanValue(value);
        return cleaned.replace("\r", "").replace("\n", "").trim();
    }

    private String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }

        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
