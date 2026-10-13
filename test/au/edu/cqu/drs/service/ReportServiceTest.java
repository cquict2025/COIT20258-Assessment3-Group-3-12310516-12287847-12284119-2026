package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for capturing and triaging public reports (FR-02, FR-03).
 * Input validation is exercised by equivalence partitioning on each field and
 * by
 * the boundary value of the description length.
 */
class ReportServiceTest {

    private TestFixtures fixtures;
    private User coordinator;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
        coordinator = fixtures.createUser("coordinator", UserRole.COORDINATOR);
    }

    @Test
    @DisplayName("TC-RP-01 a complete report is accepted and receives an "
            + "identifier and timestamp")
    void completeReportIsAccepted() {
        DisasterReport report = fixtures.validReport(DisasterType.FIRE,
                                                     "Laverton", 
                -37.8636, 144.7700, LocalDateTime.now().minusMinutes(10));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isSuccess());
        assertNotNull(result.getValue().getReportId());
        assertNotNull(result.getValue().getSubmittedAt());
        assertEquals(ReportStatus.SUBMITTED, result.getValue().getStatus());
    }

    @Test
    @DisplayName("TC-RP-12 the reporter's name never reaches the audit log")
    void reporterNameStaysOutOfAuditLog() {
        DisasterReport report = fixtures.validReport(DisasterType.FIRE,
                "Laverton", -37.8636, 144.7700,
                LocalDateTime.now().minusMinutes(10));
        report.setReporterName("Priya Shah");
        report.setReporterContact("0400 111 222");

        DisasterReport saved =
                fixtures.reportService.submitReport(report).getValue();

        var events = fixtures.auditRepository.findByEntity("DisasterReport",
                saved.getReportId());
        assertEquals(1, events.size());
        assertEquals(ReportService.PUBLIC_ACTOR, events.get(0).getActor());
        assertTrue(events.stream().noneMatch(e -> e.toString()
                .contains("Priya") || e.toString().contains("0400")));
    }

    @Test
    @DisplayName("TC-RP-02 a report with no disaster type is refused and names "
            + "the field")
    void missingTypeIsRefused() {
        DisasterReport report = fixtures.validReport(null, "Laverton", -37.86,
                                                     144.77, 
                LocalDateTime.now().minusMinutes(5));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isFailure());
        assertEquals("disasterType", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-03 the type OTHER requires a written description")
    void otherTypeRequiresDescription() {
        DisasterReport report = fixtures.validReport(DisasterType.OTHER,
                                                     "Laverton", 
                -37.86, 144.77, LocalDateTime.now().minusMinutes(5));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isFailure());
        assertEquals("otherTypeDescription", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-04 a blank suburb is refused")
    void blankSuburbIsRefused() {
        DisasterReport report = fixtures.validReport(DisasterType.FLOOD, "   ",
                -37.86, 144.77, LocalDateTime.now().minusMinutes(5));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isFailure());
        assertEquals("suburb", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-05 a latitude outside the valid range is refused")
    void latitudeOutOfRangeIsRefused() {
        DisasterReport report = fixtures.validReport(DisasterType.FLOOD,
                                                     "Laverton", 
                -91.0, 144.77, LocalDateTime.now().minusMinutes(5));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isFailure());
        assertEquals("latitude", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-06 an observed time in the future is refused")
    void futureObservedTimeIsRefused() {
        DisasterReport report = fixtures.validReport(DisasterType.STORM,
                                                     "Laverton", 
                -37.86, 144.77, LocalDateTime.now().plusHours(1));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.submitReport(report);

        assertTrue(result.isFailure());
        assertEquals("observedAt", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-07 a description at the length limit is accepted and "
            + "one over is refused")
    void descriptionLengthBoundary() {
        DisasterReport atLimit = fixtures.validReport(DisasterType.FIRE,
                                                      "Laverton", 
                -37.86, 144.77, LocalDateTime.now().minusMinutes(5));
        atLimit.setDescription(
                "x".repeat(ReportService.MAX_DESCRIPTION_LENGTH));

        DisasterReport overLimit = fixtures.validReport(DisasterType.FIRE,
                                                        "Laverton", 
                -37.86, 144.77, LocalDateTime.now().minusMinutes(5));
        overLimit.setDescription("x".repeat(ReportService.MAX_DESCRIPTION_LENGTH
                + 1));

        assertTrue(fixtures.reportService.submitReport(atLimit).isSuccess());
        ServiceResult<DisasterReport> refused =
                fixtures.reportService.submitReport(overLimit);
        assertTrue(refused.isFailure());
        assertEquals("description", refused.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-08 a draft is stored without passing the submission "
            + "rules")
    void draftIsStoredWithoutValidation() {
        DisasterReport draft = new DisasterReport();
        draft.setDisasterType(DisasterType.FIRE);
        draft.setLocation(new Location("", "", 0, 0));

        ServiceResult<DisasterReport> result =
                fixtures.reportService.saveDraft(draft);

        assertTrue(result.isSuccess());
        assertEquals(ReportStatus.DRAFT, result.getValue().getStatus());
    }

    @Test
    @DisplayName("TC-RP-09 rejecting a report without a reason is refused")
    void rejectionWithoutReasonIsRefused() {
        DisasterReport saved = fixtures.reportService.submitReport(
                fixtures.validReport(DisasterType.FIRE, "Laverton", -37.86,
                                     144.77, 
                        LocalDateTime.now().minusMinutes(5))).getValue();

        ServiceResult<DisasterReport> result =
                fixtures.reportService.decideReport(
                coordinator, saved.getReportId(), ReportStatus.REJECTED, "");

        assertTrue(result.isFailure());
        assertEquals("reason", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-RP-10 a responder cannot decide a report")
    void responderCannotDecideReport() {
        User responder = fixtures.createUser("responder", UserRole.RESPONDER);
        DisasterReport saved = fixtures.reportService.submitReport(
                fixtures.validReport(DisasterType.FIRE, "Laverton", -37.86,
                                     144.77, 
                        LocalDateTime.now().minusMinutes(5))).getValue();

        ServiceResult<DisasterReport> result =
                fixtures.reportService.decideReport(
                responder, saved.getReportId(), ReportStatus.VALIDATED, null);

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.PERMISSION_DENIED, result.getErrorCode());
    }

    @Test
    @DisplayName("TC-RP-11 a report can only be decided once")
    void reportCannotBeDecidedTwice() {
        DisasterReport saved = fixtures.reportService.submitReport(
                fixtures.validReport(DisasterType.FIRE, "Laverton", -37.86,
                                     144.77, 
                        LocalDateTime.now().minusMinutes(5))).getValue();
        fixtures.reportService.decideReport(coordinator, saved.getReportId(),
                ReportStatus.VALIDATED, null);

        ServiceResult<DisasterReport> second =
                fixtures.reportService.decideReport(
                coordinator, saved.getReportId(), ReportStatus
                        .REJECTED, "Changed my mind");

        assertTrue(second.isFailure());
        assertEquals(ErrorCode.CONFLICT, second.getErrorCode());
    }
}
