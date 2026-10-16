package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.ReportStatus;

import java.util.List;
import java.util.Optional;

/** Data-access contract for public disaster reports (FR-02, FR-03). */
public interface ReportRepository {

    /**
     * Saves a report, giving it an identifier if it has none yet.
     *
     * @param report the report to save
     * @return the saved report, with its identifier set
     */
    DisasterReport save(DisasterReport report);

    /**
     * Finds a report by its identifier.
     *
     * @param reportId the report identifier
     * @return the report, or empty if there is none
     */
    Optional<DisasterReport> findById(String reportId);

    /**
     * Lists every stored report.
     *
     * @return all reports
     */
    List<DisasterReport> findAll();

    /**
     * Finds the reports that have a given status.
     *
     * @param status the status to match
     * @return the reports with that status
     */
    List<DisasterReport> findByStatus(ReportStatus status);
}
