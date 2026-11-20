package com.orderplace.notification.mail;

import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.model.RecipientRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Fills an HTML template (templates/email/{name}.html) with the order data. Thymeleaf escapes all values. */
@Component
public class EmailRenderer {

    private final TemplateEngine engine;
    private final String webUrl;

    public EmailRenderer(TemplateEngine engine, @Value("${app.web-url}") String webUrl) {
        this.engine = engine;
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
    }

    public String render(String template, RecipientRole role, OrderEventPayload p) {
        Context ctx = new Context();
        ctx.setVariable("orderNumber", p.orderNumber());
        ctx.setVariable("customerName", p.customerName());
        ctx.setVariable("total", formatMoney(p.total(), p.currency()));
        ctx.setVariable("reason", p.reason());
        ctx.setVariable("orderLink", webUrl + (role == RecipientRole.DEALER ? "/dealer/orders/" : "/orders/") + p.orderId());
        return engine.process("email/" + template, ctx);
    }

    static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return "";
        return (currency == null ? "" : currency + " ") + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
