package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;

import java.util.List;
import java.util.Optional;

/**
 * Data-access contract for public warnings (Feature 1). Warnings are stored
 * as versions: a version, once saved, is never changed.
 */
public interface WarningRepository {

    /**
     * Stores a new version of a warning. A warning without a code is given
     * the next one, for example WRN-0003.
     *
     * @param warning the version to store, with its version number set
     * @return the same warning, with its code set
     * @throws IllegalStateException if that version is already stored,
     *                               which means someone else changed the
     *                               warning first
     */
    Warning saveVersion(Warning warning);

    /**
     * Finds the newest version of a warning.
     *
     * @param warningId the warning's code
     * @return the newest version, if the warning exists
     */
    Optional<Warning> findLatest(String warningId);

    /**
     * Lists the newest version of every warning.
     *
     * @return one entry per warning, oldest warning first
     */
    List<Warning> findLatestVersions();

    /**
     * Lists every version of a warning.
     *
     * @param warningId the warning's code
     * @return the versions, oldest first
     */
    List<Warning> findHistory(String warningId);

    /**
     * Records a department's acknowledgement.
     *
     * @param acknowledgement the acknowledgement
     * @return the same acknowledgement
     * @throws IllegalStateException if that department has already
     *                               acknowledged the warning
     */
    WarningAcknowledgement saveAcknowledgement(
            WarningAcknowledgement acknowledgement);

    /**
     * Lists the departments that have acknowledged a warning.
     *
     * @param warningId the warning's code
     * @return the acknowledgements, earliest first
     */
    List<WarningAcknowledgement> findAcknowledgements(String warningId);
}
