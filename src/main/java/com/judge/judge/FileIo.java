package com.judge.judge;

import com.judge.domain.Problem;
import com.judge.exception.JudgeException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * File I/O problems: the solution reads its input from a named file (e.g. BAI1.INP) in its
 * working directory and writes its answer to another (e.g. BAI1.OUT), as in Vietnamese
 * olympiad / Themis style contests.
 *
 * <ul>
 *   <li>{@code STDIO} — classic stdin/stdout (default, no files).</li>
 *   <li>{@code FILE} — strict: stdin is empty, stdout is discarded, only the output file counts.</li>
 *   <li>{@code FILE_OR_STDIO} — the input is offered both ways; the output file wins, stdout is the
 *       fallback when the file was not created.</li>
 * </ul>
 *
 * File names match case-insensitively (students coming from Windows write {@code bai1.inp}).
 */
public final class FileIo {

    public static final String STDIO = "STDIO";
    public static final String FILE = "FILE";
    public static final String FILE_OR_STDIO = "FILE_OR_STDIO";

    /** Plain file names only: no path separators, no leading dot. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");

    public record Spec(String mode, String inputFile, String outputFile) {
        public static final Spec NONE = new Spec(STDIO, null, null);

        public boolean isFile() {
            return !STDIO.equals(mode);
        }

        public boolean isStrict() {
            return FILE.equals(mode);
        }
    }

    private FileIo() {}

    public static Spec of(Problem p) {
        if (p == null || p.getIoMode() == null || STDIO.equals(p.getIoMode())) return Spec.NONE;
        return new Spec(p.getIoMode(), p.getInputFile(), p.getOutputFile());
    }

    /**
     * Validates and normalizes a problem's I/O settings. A blank mode with file names given means
     * {@code FILE}; {@code STDIO} drops any names.
     *
     * @throws JudgeException (400) on an invalid combination
     */
    public static Spec normalize(String mode, String inputFile, String outputFile, String checkerType) {
        String in = blankToNull(inputFile);
        String out = blankToNull(outputFile);
        String m = blankToNull(mode);
        if (m == null) m = (in == null && out == null) ? STDIO : FILE;
        m = m.toUpperCase(Locale.ROOT);

        if (STDIO.equals(m)) return Spec.NONE;
        if (!FILE.equals(m) && !FILE_OR_STDIO.equals(m)) {
            throw JudgeException.badRequest("ioMode must be STDIO, FILE or FILE_OR_STDIO");
        }
        if (in == null || out == null) {
            throw JudgeException.badRequest("ioMode " + m + " requires both inputFile and outputFile");
        }
        requireName("inputFile", in);
        requireName("outputFile", out);
        if (in.equalsIgnoreCase(out)) {
            throw JudgeException.badRequest("inputFile and outputFile must differ");
        }
        if ("INTERACTIVE".equals(checkerType)) {
            throw JudgeException.badRequest("Interactive problems cannot use file I/O");
        }
        return new Spec(m, in, out);
    }

    /** Applies normalized settings to the entity. */
    public static void apply(Problem p, Spec spec) {
        p.setIoMode(spec.mode());
        p.setInputFile(spec.inputFile());
        p.setOutputFile(spec.outputFile());
    }

    /**
     * The solution runs with its own scratch directory as cwd, so a run command that refers to
     * the compiled program or source relatively ({@code ./solution}, {@code python3 solution.py})
     * must point into /code instead.
     */
    static String absoluteRunCmd(String runCmd, String sourceFile) {
        StringBuilder sb = new StringBuilder();
        for (String token : runCmd.trim().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            if (token.startsWith("./")) {
                sb.append("/code/").append(token.substring(2));
            } else if (sourceFile != null && token.equals(sourceFile)) {
                sb.append("/code/").append(token);
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    /**
     * Shell lines that run one test case in file mode inside the batch container. The case gets a
     * fresh /box/{id} (host disk, not tmpfs, so big files don't count against the memory limit),
     * the input is copied in under its expected name plus lower/upper-case aliases, and after the
     * run the output file (matched case-insensitively) is copied to /metrics/{id}.out. If there is
     * neither a file nor (in FILE_OR_STDIO) any stdout, /metrics/{id}.nofile marks it.
     */
    static String batchCaseScript(Spec spec, long id, String containerInput, String wallSecs,
                                  String absRunCmd) {
        String box = "/box/" + id;
        String in = spec.inputFile();
        String out = spec.outputFile();
        String stdin = spec.isStrict() ? "/dev/null" : box + "/" + in;
        String stdout = spec.isStrict() ? "/dev/null" : "/metrics/" + id + ".stdout";

        StringBuilder sb = new StringBuilder();
        sb.append("rm -rf ").append(box).append(" && mkdir -p ").append(box).append('\n');
        sb.append("cp \"").append(containerInput).append("\" \"").append(box).append('/').append(in).append("\"\n");
        for (String alias : aliases(in)) {
            sb.append("ln -s \"").append(in).append("\" \"").append(box).append('/').append(alias).append("\"\n");
        }
        sb.append(String.format(
                "/usr/bin/time -q -f '%%e %%U %%S %%M %%x' -o /metrics/%d.time timeout %s /bin/sh -c 'cd %s && %s < \"%s\" > \"%s\" 2> /metrics/%d.err'\n",
                id, wallSecs, box, absRunCmd, stdin, stdout, id));
        sb.append("EC=$?\n");
        sb.append("kill -9 -1 2>/dev/null\n");
        sb.append("echo $EC > /metrics/").append(id).append(".exit\n");
        sb.append("OUTF=$(ls -1A ").append(box).append(" | grep -ixF -- \"").append(out).append("\" | head -n 1)\n");
        sb.append("if [ -n \"$OUTF\" ] && [ -f \"").append(box).append("/$OUTF\" ]; then\n");
        sb.append("  cp \"").append(box).append("/$OUTF\" /metrics/").append(id).append(".out\n");
        if (!spec.isStrict()) {
            sb.append("elif [ -s /metrics/").append(id).append(".stdout ]; then\n");
            sb.append("  mv /metrics/").append(id).append(".stdout /metrics/").append(id).append(".out\n");
        }
        sb.append("else\n");
        sb.append("  : > /metrics/").append(id).append(".nofile\n");
        sb.append("fi\n");
        sb.append("rm -rf ").append(box).append('\n');
        return sb.toString();
    }

    /** Lower/upper-case spellings of {@code name} other than itself. */
    static Set<String> aliases(String name) {
        Set<String> set = new LinkedHashSet<>();
        set.add(name.toLowerCase(Locale.ROOT));
        set.add(name.toUpperCase(Locale.ROOT));
        set.remove(name);
        return set;
    }

    private static void requireName(String field, String value) {
        if (!NAME.matcher(value).matches()) {
            throw JudgeException.badRequest(field + " must be a plain file name "
                    + "(letters, digits, '_', '-', '.'; max 64 chars), got: " + value);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
