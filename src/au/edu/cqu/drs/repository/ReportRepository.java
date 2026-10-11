package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.ReportStatus;

import java.util.List;
import java.util.Optional;

/** Data-access contract for public disaster reports (FR-02, FR-03). */
public interface ReportRepository {

    DisasterReport save(DisasterReport report);

    Optional<DisasterReport> findById(String reportId);

    List<DisasterReport> findAll();

    List<DisasterReport> findByStatus(ReportStatus status);
}
