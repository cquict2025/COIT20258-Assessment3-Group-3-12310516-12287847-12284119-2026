package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards for the two problems the visual test found on the warning
 * screens: Enter publishing a warning, and cancelling the wrong one.
 */
class WarningScreenSafetyTest {

    /**
     * Checks that none of the warning screens has a default button, so
     * pressing Enter cannot issue or acknowledge a warning.
     *
     * @throws IOException if a view file cannot be read
     */
    @Test
    @DisplayName("TC-WU-01 no warning screen has a default button, so Enter "
            + "cannot issue or acknowledge a warning")
    void noDefaultButtons() throws IOException {
        for (String view : new String[] {"warning-main.fxml",
            "warning-ack.fxml", "warning-board.fxml"}) {
            assertFalse(read(view).contains("defaultButton"),
                    view + " must not have a default button");
        }
    }

    /**
     * Checks that the cancel confirmation question names the
     * warning's code, its level and its affected area.
     */
    @Test
    @DisplayName("TC-WU-02 the cancel question names the warning, its level "
            + "and its area")
    void cancelQuestionNamesTheWarning() {
        Warning warning = new Warning("INC-0001",
                WarningLevel.EMERGENCY_WARNING, "Laverton, Altona North",
                "Fire", LocalDateTime.now().plusHours(2));
        warning.setWarningId("WRN-0001");

        String question = WarningMainController.cancelQuestion(warning);

        assertTrue(question.contains("WRN-0001"));
        assertTrue(question.contains("Emergency Warning"));
        assertTrue(question.contains("Laverton, Altona North"));
    }

    /**
     * Reads an FXML view from the classpath as text.
     *
     * @param view the file name of the view
     * @return the contents of the view file
     * @throws IOException if the view file cannot be read
     */
    private static String read(String view) throws IOException {
        try (InputStream in = WarningScreenSafetyTest.class
                .getResourceAsStream("/au/edu/cqu/drs/view/" + view)) {
            assertNotNull(in, view + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Describes this test class and the fixture it uses.
     *
     * @return the class name and what it checks
     */
    @Override
    public String toString() {
        return "WarningScreenSafetyTest[checks the warning screens for "
                + "default buttons and the cancel question]";
    }
}
