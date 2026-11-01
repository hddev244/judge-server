# File I/O problems

Vietnamese olympiad / Themis-style problems where the solution reads `BAI1.INP` and writes
`BAI1.OUT` in its working directory instead of using stdin/stdout.

## Problem settings

| Field | Values |
|---|---|
| `ioMode` | `STDIO` (default) · `FILE` (strict) · `FILE_OR_STDIO` (lenient) |
| `inputFile` / `outputFile` | plain file names, `[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}`, must differ (case-insensitively) |

Accepted in `POST/PUT /api/v1/admin/problems` (JSON, `ioMode` null on PUT keeps the current mode),
ZIP `problem.yml` (`io_mode`/`input_file`/`output_file`, camelCase also accepted) and the
`ProblemPackage` used by grader sync (fields omitted for STDIO so existing package hashes are
unchanged). File I/O cannot be combined with an `INTERACTIVE` checker. Advertised on
`GET /api/v1/meta` as capability `FILE_IO`.

## How a case runs (`FileIo.batchCaseScript`)

1. Fresh `/box/{caseId}` — a host-disk dir (`workDir/box`), **not** tmpfs: tmpfs pages count
   against the container memory cgroup and big inputs would cause false MLE.
2. The case input is copied in as `inputFile`, plus lower/upper-case symlink aliases.
3. The solution runs with `/box/{caseId}` as cwd; relative run commands are rewritten into
   `/code` (`./solution` → `/code/solution`, `python3 solution.py` → `python3 /code/solution.py`).
   - `FILE`: stdin `/dev/null`, stdout discarded.
   - `FILE_OR_STDIO`: stdin is the input file, stdout captured as a fallback.
4. After the run (and `kill -9 -1`), the output file is matched case-insensitively and copied to
   `/metrics/{id}.out`; the time limit only covers the solution, not the copies.
5. No output file (and no stdout in lenient mode) on a clean exit → `RunResult.outputFileMissing`
   → verdict `WA`, stderr `Output file X was not created`.
