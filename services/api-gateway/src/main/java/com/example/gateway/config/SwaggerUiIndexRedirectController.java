package com.example.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.result.view.RedirectView;

@Controller
public class SwaggerUiIndexRedirectController {

    @GetMapping("/swagger-ui/index.html")
    public RedirectView redirectSwaggerUiIndex() {
        RedirectView redirectView = new RedirectView("/webjars/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config");
        redirectView.setStatusCode(HttpStatus.FOUND);
        return redirectView;
    }
}
