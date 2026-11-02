package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.ConfigurationRequests.Approve;
import com.company.leadershipintelligence.api.request.ConfigurationRequests.CreateDraft;
import com.company.leadershipintelligence.api.request.ConfigurationRequests.Reject;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.configuration.ConfigurationReviewService;
import com.company.leadershipintelligence.service.configuration.FixtureSpec;
import com.company.leadershipintelligence.service.configuration.Manager;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationLayer;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationVersionDetail;
import com.company.leadershipintelligence.service.query.ConfigurationViews.FixtureView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewResult;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ReviewView;
import com.company.leadershipintelligence.service.query.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

/**
 * The configuration management endpoints (decisions D52 to D58), shared by the two ways in: the platform administrator reaches the
 * platform default under {@code /api/v1/platform/configuration}, owners and administrators their tenant and organization overrides under
 * {@code /api/v1/configuration}. The subclass says who the caller is from the verified token (never from a request); the workflow, the
 * second-person rule and the fixtures gate are in {@link ConfigurationReviewService}. A version or layer outside the caller's reach
 * looks missing.
 */
public abstract class ConfigurationApi {

    private final ConfigurationReviewService review;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    protected ConfigurationApi(ConfigurationReviewService review, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.review = review;
        this.json = json;
    }

    /** The caller, from the verified token, with the checks that belong to this path. */
    protected abstract Manager manager(Jwt jwt);

    /** The layer a draft belongs to: always the platform default on the platform path. */
    protected abstract ConfigurationTarget target(Manager manager, String scope, String organizationId);

    @Operation(summary = "The layers the caller may manage, each with its active version and the one being worked on")
    @GetMapping("/layers")
    public List<ConfigurationLayer> layers(@AuthenticationPrincipal Jwt jwt) {
        return review.layers(manager(jwt));
    }

    @Operation(summary = "The versions of one layer, newest first (without their documents)")
    @GetMapping("/versions")
    public List<ConfigurationVersionDetail> versions(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String scope, @RequestParam(required = false) String organizationId) {
        Manager manager = manager(jwt);
        return review.versions(manager, target(manager, scope, organizationId));
    }

    @Operation(summary = "One version with its document and its review record")
    @GetMapping("/versions/{versionId}")
    public ConfigurationVersionDetail version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.get(manager(jwt), versionId);
    }

    @Operation(summary = "A new draft of a layer: the given document, or a copy of the active version")
    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigurationVersionDetail createDraft(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDraft request) {
        Manager manager = manager(jwt);
        return review.createDraft(manager, target(manager, request.scope(), request.organizationId()), request.document() == null ? null : request.document().toString());
    }

    @Operation(summary = "Replace the document of a draft (the whole configuration document)")
    @PutMapping("/versions/{versionId}")
    public ConfigurationVersionDetail updateDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId, @RequestBody JsonNode document) {
        return review.updateDraft(manager(jwt), versionId, document.toString());
    }

    @Operation(summary = "A new draft with the content of any version of the layer (how a change is rolled back)")
    @PostMapping("/versions/{versionId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigurationVersionDetail copy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.copyAsDraft(manager(jwt), versionId);
    }

    @Operation(summary = "Every problem of a version, without changing it")
    @PostMapping("/versions/{versionId}/validate")
    public ValidationResult validate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.check(manager(jwt), versionId);
    }

    @Operation(summary = "Score the fixtures, and the tenant's recent assessments when allowed, before and after, in memory only (the read is audited)")
    @PostMapping("/versions/{versionId}/preview")
    public PreviewResult preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.preview(manager(jwt), versionId);
    }

    @Operation(summary = "Submit a draft for approval: it is validated (every problem is returned) and previewed, then waits for another person")
    @PostMapping("/versions/{versionId}/submit")
    public ReviewView submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.submit(manager(jwt), versionId);
    }

    @Operation(summary = "Approve and activate a version awaiting approval; refused for anyone who worked on it, and while a fixture fails")
    @PostMapping("/versions/{versionId}/approve")
    public ConfigurationVersionDetail approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId, @Valid @RequestBody Approve request) {
        return review.approve(manager(jwt), versionId, request.note(), request.effectiveFrom(), request.calibrationReference(), request.knownLimitations());
    }

    @Operation(summary = "Send a version awaiting approval back to draft with the reason")
    @PostMapping("/versions/{versionId}/reject")
    public ConfigurationVersionDetail reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId, @Valid @RequestBody Reject request) {
        return review.reject(manager(jwt), versionId, request.comment());
    }

    @Operation(summary = "Stop using an active version for new scoring runs")
    @PostMapping("/versions/{versionId}/pause")
    public ConfigurationVersionDetail pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.pause(manager(jwt), versionId);
    }

    @Operation(summary = "Use a paused version again")
    @PostMapping("/versions/{versionId}/resume")
    public ConfigurationVersionDetail resume(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.resume(manager(jwt), versionId);
    }

    @Operation(summary = "Retire a version; it stays available to explain history")
    @PostMapping("/versions/{versionId}/archive")
    public ConfigurationVersionDetail archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID versionId) {
        return review.archive(manager(jwt), versionId);
    }

    @Operation(summary = "The fixtures of a layer")
    @GetMapping("/layers/{layerId}/fixtures")
    public List<FixtureView> fixtures(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID layerId) {
        return review.fixtures(manager(jwt), layerId);
    }

    @Operation(summary = "Add a fixture to a layer")
    @PostMapping("/layers/{layerId}/fixtures")
    @ResponseStatus(HttpStatus.CREATED)
    public FixtureView addFixture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID layerId, @RequestBody FixtureSpec spec) {
        return review.saveFixture(manager(jwt), layerId, null, spec);
    }

    @Operation(summary = "Replace a fixture")
    @PutMapping("/layers/{layerId}/fixtures/{fixtureId}")
    public FixtureView replaceFixture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID layerId, @PathVariable UUID fixtureId, @RequestBody FixtureSpec spec) {
        return review.saveFixture(manager(jwt), layerId, fixtureId, spec);
    }

    @Operation(summary = "Delete a fixture")
    @DeleteMapping("/layers/{layerId}/fixtures/{fixtureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFixture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID layerId, @PathVariable UUID fixtureId) {
        review.deleteFixture(manager(jwt), layerId, fixtureId);
    }

    /** A tenant-path layer from the request, never the platform default. */
    protected static ConfigurationTarget tenantTarget(Manager manager, String scope, String organizationId) {
        if ("ORGANIZATION".equals(scope)) {
            if (organizationId == null || organizationId.isBlank()) {
                throw new ApiRequestException("an organization layer needs an organizationId");
            }
            return ConfigurationTarget.organization(manager.caller().tenantId(), new OrganizationId(organizationId));
        }
        if (scope == null || "TENANT".equals(scope)) {
            return ConfigurationTarget.tenant(manager.caller().tenantId());
        }
        throw new ApiRequestException("scope must be TENANT or ORGANIZATION");
    }
}
