package com.orderplace.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderplace.common.events.OrderEventPayload;
import com.orderplace.notification.model.RecipientRole;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Renders the real templates with a stand-alone Thymeleaf engine (no Spring context needed). */
class EmailRendererTest {

    private final UUID orderId = UUID.randomUUID();
    private final EmailRenderer renderer = new EmailRenderer(engine(), "http://localhost:5173/");

    private static TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private OrderEventPayload payload(String reason) {
        return new OrderEventPayload(orderId, "OP-2026-000007", UUID.randomUUID(), "Demo Customer One",
                UUID.randomUUID(), "c@x.com", "d@x.com", new BigDecimal("45000"), "INR", reason);
    }

    @Test
    void everyTemplateRendersOrderNumberAndAmount() {
        for (String template : new String[] {"OrderSubmitted", "OrderCancelled", "OrderApproved", "OrderEdited",
                "OrderRejected", "OrderReadyForDelivery"}) {
            String html = renderer.render(template, RecipientRole.CUSTOMER, payload(null));
            assertThat(html).as(template).contains("OP-2026-000007");
            if (!template.equals("OrderRejected")) assertThat(html).as(template).contains("INR 45000.00");
            assertThat(html).as(template).doesNotContain("${");
        }
    }

    @Test
    void linksPointToTheRightPlaceForEachRole() {
        assertThat(renderer.render("OrderApproved", RecipientRole.CUSTOMER, payload(null)))
                .contains("http://localhost:5173/orders/" + orderId);
        assertThat(renderer.render("OrderSubmitted", RecipientRole.DEALER, payload(null)))
                .contains("http://localhost:5173/dealer/orders/" + orderId);
    }

    @Test
    void reasonIsShownWhenPresentAndHiddenWhenAbsent() {
        assertThat(renderer.render("OrderRejected", RecipientRole.CUSTOMER, payload("Item discontinued")))
                .contains("Item discontinued");
        assertThat(renderer.render("OrderRejected", RecipientRole.CUSTOMER, payload(null)))
                .doesNotContain("Reason:");
    }

    @Test
    void userTextIsEscaped() {
        String html = renderer.render("OrderRejected", RecipientRole.CUSTOMER, payload("<script>alert(1)</script>"));

        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
    }

    @Test
    void moneyIsFormattedWithTwoDecimals() {
        assertThat(EmailRenderer.formatMoney(new BigDecimal("1200"), "INR")).isEqualTo("INR 1200.00");
        assertThat(EmailRenderer.formatMoney(null, "INR")).isEmpty();
    }
}
