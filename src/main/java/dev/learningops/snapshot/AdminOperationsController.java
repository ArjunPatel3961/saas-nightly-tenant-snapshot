package dev.learningops.snapshot;

import java.util.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants")
public class AdminOperationsController {
    private final TenantSnapshotService service;

    public AdminOperationsController(TenantSnapshotService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantAccount onboard(@RequestBody TenantAccount account) { return service.onboard(account); }

    @GetMapping
    Collection<TenantAccount> list() { return service.accounts(); }

    @PatchMapping("/{tenantId}/status/{status}")
    TenantAccount status(@PathVariable String tenantId, @PathVariable TenantAccount.Status status) {
        return service.changeStatus(tenantId, status);
    }

    @PostMapping("/{tenantId}/snapshots")
    TenantSnapshotService.SnapshotReceipt snapshot(@PathVariable String tenantId) {
        return service.snapshot(tenantId);
    }

    @ExceptionHandler(TenantSnapshotService.TenantNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse missing(TenantSnapshotService.TenantNotFoundException error) {
        return new ErrorResponse(error.getMessage());
    }

    @ExceptionHandler(InfraiStorageClient.InfraiException.class)
    org.springframework.http.ResponseEntity<ErrorResponse> rejected(InfraiStorageClient.InfraiException error) {
        HttpStatus status = HttpStatus.resolve(error.httpStatus());
        return org.springframework.http.ResponseEntity.status(status == null ? HttpStatus.BAD_GATEWAY : status)
            .body(new ErrorResponse(error.code() + ": " + error.message()));
    }

    record ErrorResponse(String message) {}
}
