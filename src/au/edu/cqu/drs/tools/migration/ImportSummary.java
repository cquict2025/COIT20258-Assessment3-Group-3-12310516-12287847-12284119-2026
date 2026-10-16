package au.edu.cqu.drs.tools.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts of what an import did, per table, in the order the tables were
 * sent, plus the reason for every refused record.
 */
public class ImportSummary {

    private final Map<String, int[]> counts = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    /**
     * Records a record the server added.
     *
     * @param table the table name, for example "reports"
     */
    public void added(String table) {
        row(table)[0]++;
    }

    /**
     * Records a record the server already held.
     *
     * @param table the table name
     */
    public void alreadyThere(String table) {
        row(table)[1]++;
    }

    /**
     * Records a record the server refused.
     *
     * @param table  the table name
     * @param reason the record and the server's message
     */
    public void refused(String table, String reason) {
        row(table)[2]++;
        this.problems.add(table + ": " + reason);
    }

    /**
     * Returns how many records of a table were added.
     *
     * @param table the table name
     * @return the count
     */
    public int getAdded(String table) {
        return row(table)[0];
    }

    /**
     * Returns how many records of a table were already there.
     *
     * @param table the table name
     * @return the count
     */
    public int getAlreadyThere(String table) {
        return row(table)[1];
    }

    /**
     * Returns how many records were refused in all.
     *
     * @return the count
     */
    public int getRefused() {
        return this.counts.values().stream().mapToInt(row -> row[2]).sum();
    }

    /**
     * Lists the refused records with the server's reasons.
     *
     * @return the problems, in the order they happened
     */
    public List<String> getProblems() {
        return List.copyOf(this.problems);
    }

    /**
     * Finds the counts for a table, starting a new row of zeros the first time
     * the table is seen.
     *
     * @param table the table name
     * @return the added, already-there and refused counts, in that order
     */
    private int[] row(String table) {
        return this.counts.computeIfAbsent(table, name -> new int[3]);
    }

    /**
     * Prints the counts as a table, followed by any problems.
     *
     * @return the summary text
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(String.format(
                "%-15s %6s %8s %8s%n", "Table", "Added", "Already",
                "Refused"));
        for (Map.Entry<String, int[]> entry : this.counts.entrySet()) {
            int[] row = entry.getValue();
            text.append(String.format("%-15s %6d %8d %8d%n", entry.getKey(),
                    row[0], row[1], row[2]));
        }
        for (String problem : this.problems) {
            text.append("Refused ").append(problem).append('\n');
        }
        return text.toString();
    }
}
