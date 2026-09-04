package backend.demo.adapter.in.web;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("demo")
public final class DemoPageController {

    private static final MediaType UTF8_HTML = new MediaType(
            "text", "html", StandardCharsets.UTF_8);

    @GetMapping(value = { "/demo", "/demo/" }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> demoPage() {
        return ResponseEntity.ok()
                .contentType(UTF8_HTML)
                .cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("static/demo/index.html"));
    }
}
