package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.ReportRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Capture and triage of public disaster reports (UC-02, UC-03, FR-02, FR-03).
 * A member of the public may submit without an account (UR-01), but only a
 * coordinator may decide what happens to a report afterwards.
 */
public class ReportService {

    /** Audit actor for anything submitted by a member of the public. */
    public static final String PUBLIC_ACTOR = "public";

    /**
     * Longest description accepted, so that a single report cannot fill the
     * display.
     */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final ReportRepository reportRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    /**
     * Creates a report service.
     *
     * @param reportRepository      the report repository
     * @param authenticationService the authentication service
     * @param auditService          the audit service
     */
    public ReportService(ReportRepository reportRepository,
                         AuthenticationService authenticationService,
                         AuditService auditService) {
        this.reportRepository = reportRepository;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    /**
     * Validates and stores a new public report.
     *
     * @param report the report captured on the form
     * @return the saved report with its identifier, or a typed validation
     * failure
     */
    public ServiceResult<DisasterReport> submitReport(DisasterReport report) {
        if (report == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                                         "No report was supplied.");
        }
        if (report.getDisasterType() == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Select the type of disaster.", "disasterType");
        }
        if (report.getDisasterType() == DisasterType.OTHER
                && (report.getOtherTypeDescription() == null
                || report.getOtherTypeDescription().isBlank())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Describe the disaster type when 'Other' is selected.",
                            "otherTypeDescription");
        }

        Location location = report.getLocation();
        if (location == null || location.getSuburb() == null
                || location.getSuburb().isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Enter the suburb where the disaster was observed.",
                            "suburb");
        }
        if (location.getLatitude() < -90 || location.getLatitude() > 90) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Latitude must be between -90 and 90.", "latitude");
        }
        if (location.getLongitude() < -180 || location.getLongitude() > 180) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Longitude must be between -180 and 180.", "longitude");
        }

        if (report.getObservedAt() == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Enter when the disaster was observed.", "observedAt");
        }
        if (report.getObservedAt().isAfter(LocalDateTime.now())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "The observed time cannot be in the future.", "observedAt");
        }
        if (report.getDescription() == null
                || report.getDescription().isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Describe what you observed.", "description");
        }
        if (report.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Keep the description to " + MAX_DESCRIPTION_LENGTH
                            + " characters or fewer.",
                    "description");
        }

        report.setStatus(ReportStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());
        DisasterReport saved = this.reportRepository.save(report);
        // The actor is always "public": the reporter's name is personal
        // data that the report store encrypts, so it must not be copied
        // into the audit log in plain text. The report code links the two.
        this.auditService.record(PUBLIC_ACTOR,
                "SUBMIT_REPORT", "DisasterReport", saved.getReportId(),
                saved.getTypeLabel() + " at " + location.getSuburb());
        return ServiceResult.success(saved);
    }

    /**
     * Saves an incomplete report so that a stressed reporter does not lose
     * input (UC-02).
     *
     * @param report the report, which may be incomplete
     * @return the saved draft with its reference, or the reason it was not
     *         saved
     */
    public ServiceResult<DisasterReport> saveDraft(DisasterReport report) {
        if (report == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                                         "No report was supplied.");
        }
        report.setStatus(ReportStatus.DRAFT);
        return ServiceResult.success(this.reportRepository.save(report));
    }

    /**
     * Records a coordinator's decision about a submitted report (UC-03).
     *
     * @param coordinator the signed-in coordinator
     * @param reportId    the report being decided
     * @param decision    the new status: VALIDATED, REJECTED or LINKED
     * @param reason the reason, mandatory for anything other than VALIDATED
     * @return the updated report, or a typed failure
     */
    public ServiceResult<DisasterReport> decideReport(User coordinator,
            String reportId, 
                    ReportStatus decision, String reason) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can decide a report.");
        }
        Optional<DisasterReport> found =
                this.reportRepository.findById(reportId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That report no longer exists.");
        }
        if (decision != ReportStatus.VALIDATED
                && decision != ReportStatus.REJECTED
                && decision != ReportStatus.LINKED) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "A decision must be Validated, Rejected or Linked.",
                            "decision");
        }
        if (decision != ReportStatus.VALIDATED
                && (reason == null || reason.isBlank())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Give a reason for this decision.", "reason");
        }

        DisasterReport report = found.get();
        if (report.getStatus() != ReportStatus.SUBMITTED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Only a submitted report can be decided. This report is "
                            + report.getStatus().getDisplayName() + ".");
        }

        report.setStatus(decision);
        report.setDecisionReason(reason);
        DisasterReport saved = this.reportRepository.save(report);
        this.auditService.record(coordinator.getUsername(), "DECIDE_REPORT",
                            "DisasterReport", 
                saved.getReportId(), decision.name()
                        + (reason == null ? "" : ": " + reason));
        return ServiceResult.success(saved);
    }

    /**
     * Returns the reports that have the given status.
     *
     * @param status the status to match
     * @return the matching reports
     */
    public List<DisasterReport> findByStatus(ReportStatus status) {
        return this.reportRepository.findByStatus(status);
    }

    /**
     * Returns every disaster report.
     *
     * @return all reports
     */
    public List<DisasterReport> findAll() {
        return this.reportRepository.findAll();
    }

    /**
     * Finds a report by its identifier.
     *
     * @param reportId the report
     * @return the report, or empty when it does not exist
     */
    public Optional<DisasterReport> findById(String reportId) {
        return this.reportRepository.findById(reportId);
    }

    /**
     * Describes what this service does.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "ReportService: validates and stores disaster reports";
    }
}
