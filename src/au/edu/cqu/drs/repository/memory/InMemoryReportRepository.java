package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.repository.ReportRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory test double for disaster reports. */
public class InMemoryReportRepository implements ReportRepository {

    private final Map<String, DisasterReport> reportsById =
            new LinkedHashMap<>();
    private int sequence = 0;

    /**
     * Stores a report, replacing any stored one with the same identifier.
     * A new report is given the next RPT- identifier.
     *
     * @param report the report to store
     * @return the same report, with its identifier set
     */
    @Override
    public DisasterReport save(DisasterReport report) {
        if (report.getReportId() == null) {
            this.sequence++;
            report.setReportId(String.format("RPT-%04d", this.sequence));
        }
        this.reportsById.put(report.getReportId(), report);
        return report;
    }

    /**
     * Finds a report by its identifier.
     *
     * @param reportId the report identifier
     * @return the report, or empty if there is none
     */
    @Override
    public Optional<DisasterReport> findById(String reportId) {
        return Optional.ofNullable(this.reportsById.get(reportId));
    }

    /**
     * Lists every stored report.
     *
     * @return a new list of all reports, in the order stored
     */
    @Override
    public List<DisasterReport> findAll() {
        return new ArrayList<>(this.reportsById.values());
    }

    /**
     * Finds the reports that have a given status.
     *
     * @param status the status to match
     * @return the reports with that status
     */
    @Override
    public List<DisasterReport> findByStatus(ReportStatus status) {
        return this.reportsById.values().stream()
                .filter(r -> r.getStatus() == status)
                .toList();
    }

    /**
     * Describes the repository.
     *
     * @return how many reports it holds
     */
    @Override
    public String toString() {
        return "InMemoryReportRepository: " + this.reportsById.size()
                + " report(s)";
    }
}
