package in.trendsfit.web;

import in.trendsfit.service.SupportService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer side of the support chat. Access to a conversation needs its secret token (header X-Support-Token, or ?token=). */
@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService support;

    public SupportController(SupportService support) { this.support = support; }

    public record StartReq(String name, String email, String orderNumber, String message) {}

    @PostMapping("/conversations")
    public Map<String, Object> start(@RequestBody StartReq r) { return support.start(r.name(), r.email(), r.orderNumber(), r.message()); }

    @GetMapping("/conversations/{id}")
    public Map<String, Object> view(@PathVariable long id, @RequestHeader(value = "X-Support-Token", required = false) String header,
                                    @RequestParam(value = "token", required = false) String param) {
        return support.customerView(id, header != null ? header : param);
    }

    public record MessageReq(String body) {}

    @PostMapping("/conversations/{id}/messages")
    public Map<String, Object> send(@PathVariable long id, @RequestHeader(value = "X-Support-Token", required = false) String header,
                                    @RequestParam(value = "token", required = false) String param, @RequestBody MessageReq r) {
        return support.customerSend(id, header != null ? header : param, r.body());
    }
}
