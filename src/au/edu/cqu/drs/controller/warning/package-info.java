/**
 * The client screens for Feature 1, public warnings and evacuation
 * notices.
 *
 * <p>The {@link au.edu.cqu.drs.controller.warning.WarningMainController}
 * drives the staff warnings screen, where a coordinator issues, updates
 * and cancels warnings and sees which departments have acknowledged each
 * one. The
 * {@link au.edu.cqu.drs.controller.warning.WarningAckController} is the
 * department liaison's screen for acknowledging the warnings in force,
 * and the
 * {@link au.edu.cqu.drs.controller.warning.WarningBoardController} is the
 * public board, opened from the sign-in screen without an account. The
 * screens reach the server only through
 * {@link au.edu.cqu.drs.controller.warning.WarningCalls}, never a service
 * or repository, and show warning levels in their colours with
 * {@link au.edu.cqu.drs.controller.warning.LevelCell}.</p>
 */
package au.edu.cqu.drs.controller.warning;
