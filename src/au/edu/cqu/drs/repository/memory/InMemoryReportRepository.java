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

    @Override
    public DisasterReport save(DisasterReport report) {
        if (report.getReportId() == null) {
            this.sequence++;
            report.setReportId(String.format("RPT-%04d", this.sequence));
        }
        this.reportsById.put(report.getReportId(), report);
        return report;
    }

    @Override
    public Optional<DisasterReport> findById(String reportId) {
        return Optional.ofNullable(this.reportsById.get(reportId));
    }

    @Override
    public List<DisasterReport> findAll() {
        return new ArrayList<>(this.reportsById.values());
    }

    @Override
    public List<DisasterReport> findByStatus(ReportStatus status) {
        return this.reportsById.values().stream()
                .filter(r -> r.getStatus() == status)
                .toList();
    }
}
