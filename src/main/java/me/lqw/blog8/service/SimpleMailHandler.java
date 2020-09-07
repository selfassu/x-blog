package me.lqw.blog8.service;

import me.lqw.blog8.model.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/**
 * 邮件处理器
 *
 * @author liqiwen
 * @version 1.0
 * @since 1.2
 */
@Component
public class SimpleMailHandler {


    private final SpringTemplateEngine templateEngine;


    /**
     * 日志记录
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

    /**
     * 邮件 sender
     */
    private final JavaMailSender javaMailSender;

    public SimpleMailHandler(JavaMailSender javaMailSender, SpringTemplateEngine templateEngine) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * 发送邮件
     *
     * @throws MessagingException MessagingException
     */
    public void sendEmail() throws MessagingException {

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false);
        helper.setFrom("228794754@qq.com");
        helper.setTo("liqiwen@lppz.com");
        helper.setSubject("主题：模板邮件");
        helper.setText("<h1>测试邮件</h1>", true);

        javaMailSender.send(mimeMessage);
    }


    public void sendTemplateMail(String template, Comment comment) throws MessagingException {

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
        helper.setFrom("228794754@qq.com");
        helper.setTo("liqiwen@lppz.com");
        helper.setSubject("主题：模板邮件");


        Context context = new Context();
        context.setVariable("comment", comment);

        String commentHtml = templateEngine.process(template, context);
        logger.info("commentHtml >> " + commentHtml);
        helper.setText(commentHtml, true);

        javaMailSender.send(mimeMessage);

    }
}
