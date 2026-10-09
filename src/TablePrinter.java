import java.io.PrintStream;
import java.util.List;

// Prints a bordered ASCII table:
//   +------+------+
//   | Name | Type |
//   +------+------+
public class TablePrinter {
    public static void print(String[] headers, List<String[]> rows, String indent, PrintStream out) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) widths[i] = headers[i].length();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) widths[i] = Math.max(widths[i], row[i].length());
        }
        separator(widths, indent, out);
        row(headers, widths, indent, out);
        separator(widths, indent, out);
        for (String[] r : rows) row(r, widths, indent, out);
        separator(widths, indent, out);
    }

    private static void row(String[] cells, int[] widths, String indent, PrintStream out) {
        StringBuilder sb = new StringBuilder(indent + "|");
        for (int i = 0; i < cells.length; i++) {
            sb.append(' ').append(String.format("%-" + widths[i] + "s", cells[i])).append(" |");
        }
        out.println(sb);
    }

    private static void separator(int[] widths, String indent, PrintStream out) {
        StringBuilder sb = new StringBuilder(indent + "+");
        for (int w : widths) sb.append("-".repeat(w + 2)).append('+');
        out.println(sb);
    }
}
