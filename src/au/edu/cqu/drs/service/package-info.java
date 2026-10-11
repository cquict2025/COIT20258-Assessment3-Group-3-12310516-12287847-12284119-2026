/**
 * Application services: every business rule in the system.
 *
 * <p>Each service exposes the commands of one or more use cases and returns a
 * {@link au.edu.cqu.drs.service.ServiceResult}, which carries either the value
 * produced or a typed {@link au.edu.cqu.drs.service.ErrorCode}, a message
 * written for the person reading the screen, and the name of the field
 * concerned.</p>
 *
 * <p>Validation, scoring, lifecycle rules, authorisation and audit writing all
 * live here rather than in the controllers. The services depend only on the
 * repository interfaces, which is what allows the whole JUnit suite to run
 * without starting JavaFX and without a database file.</p>
 */
package au.edu.cqu.drs.service;
