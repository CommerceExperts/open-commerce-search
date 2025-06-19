package de.cxp.ocs.api;

import de.cxp.ocs.configmanagement.dto.ConfigRequest;
import de.cxp.ocs.configmanagement.dto.ConfigResponse;
import de.cxp.ocs.configmanagement.dto.RollbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/config-api/v1")
@RequiredArgsConstructor
public class ConfigController {
    private final ConfigService configService;

    @GetMapping("/{service}")
    public ConfigResponse getLatest(@PathVariable String service) {
        return configService.getLatest(service);
    }

    @PostMapping("/{service}")
    public void createConfig(@PathVariable String service, @RequestBody ConfigRequest request) {
        configService.createNewConfig(service, request);
    }

    @GetMapping("/{service}/history")
    public Page<ConfigResponse> getHistory(@PathVariable String service, Pageable pageable) {
        return configService.getHistory(service, pageable);
    }

    @PostMapping("/{service}/rollback")
    public void rollback(@PathVariable String service, @RequestBody RollbackRequest request) {
        configService.rollback(service, request);
    }
}
