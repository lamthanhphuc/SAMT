window.onload = function () {
  window.ui = SwaggerUIBundle({
    configUrl: "/v3/api-docs/swagger-config",
    url: "",
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout"
  });
};
