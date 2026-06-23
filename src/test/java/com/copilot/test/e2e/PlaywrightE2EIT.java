package com.copilot.test.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PlaywrightE2EIT {

    @LocalServerPort
    int port;

    @Test
    void swaggerUiLoads() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();
            String url = "http://localhost:" + port + "/swagger-ui/index.html";
            page.navigate(url);
            String content = page.content();
                        assertThat(content).containsIgnoringCase("swagger");
            browser.close();
        }
    }
}
