package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.repository.WarningRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory warnings, used by the tests and by a server running with
 * {@code repository=memory}. Methods are synchronized because the server
 * calls them from many worker threads.
 */
public class InMemoryWarningRepository implements WarningRepository {

    private final Map<String, List<Warning>> versions = new LinkedHashMap<>();
    private final List<WarningAcknowledgement> acknowledgements =
            new ArrayList<>();
    private int sequence;

    /**
     * Stores a copy of a new version.
     *
     * @param warning the version to store
     * @return the same warning, with its code set
     */
    @Override
    public synchronized Warning saveVersion(Warning warning) {
        if (warning.getWarningId() == null) {
            this.sequence++;
            warning.setWarningId(String.format("WRN-%04d", this.sequence));
        }
        List<Warning> history = this.versions.computeIfAbsent(
                warning.getWarningId(), code -> new ArrayList<>());
        for (Warning stored : history) {
            if (stored.getVersion() == warning.getVersion()) {
                throw new IllegalStateException(warning.getWarningId()
                        + " version " + warning.getVersion()
                        + " is already stored.");
            }
        }
        history.add(warning.copy());
        return warning;
    }

    /**
     * Finds the newest version of a warning.
     *
     * @param warningId the code
     * @return a copy of the newest version, if any
     */
    @Override
    public synchronized Optional<Warning> findLatest(String warningId) {
        return this.versions.getOrDefault(warningId, List.of()).stream()
                .max(Comparator.comparingInt(Warning::getVersion))
                .map(Warning::copy);
    }

    /**
     * Lists the newest version of every warning.
     *
     * @return copies, oldest warning first
     */
    @Override
    public synchronized List<Warning> findLatestVersions() {
        List<Warning> latest = new ArrayList<>();
        for (String code : this.versions.keySet()) {
            findLatest(code).ifPresent(latest::add);
        }
        return latest;
    }

    /**
     * Lists every version of a warning.
     *
     * @param warningId the code
     * @return copies, oldest first
     */
    @Override
    public synchronized List<Warning> findHistory(String warningId) {
        List<Warning> history = new ArrayList<>();
        for (Warning stored : this.versions.getOrDefault(warningId,
                List.of())) {
            history.add(stored.copy());
        }
        history.sort(Comparator.comparingInt(Warning::getVersion));
        return history;
    }

    /**
     * Records an acknowledgement.
     *
     * @param acknowledgement the acknowledgement
     * @return the same acknowledgement
     */
    @Override
    public synchronized WarningAcknowledgement saveAcknowledgement(
            WarningAcknowledgement acknowledgement) {
        for (WarningAcknowledgement stored : this.acknowledgements) {
            if (stored.getWarningId().equals(acknowledgement.getWarningId())
                    && stored.getDepartment()
                            == acknowledgement.getDepartment()) {
                throw new IllegalStateException(acknowledgement
                        .getDepartment() + " has already acknowledged "
                        + acknowledgement.getWarningId() + ".");
            }
        }
        this.acknowledgements.add(acknowledgement);
        return acknowledgement;
    }

    /**
     * Lists the acknowledgements of a warning.
     *
     * @param warningId the code
     * @return the acknowledgements, earliest first
     */
    @Override
    public synchronized List<WarningAcknowledgement> findAcknowledgements(
            String warningId) {
        List<WarningAcknowledgement> found = new ArrayList<>();
        for (WarningAcknowledgement stored : this.acknowledgements) {
            if (stored.getWarningId().equals(warningId)) {
                found.add(stored);
            }
        }
        return found;
    }

    /**
     * Describes the repository.
     *
     * @return how many warnings it holds
     */
    @Override
    public synchronized String toString() {
        return "InMemoryWarningRepository: " + this.versions.size()
                + " warning(s)";
    }
}
