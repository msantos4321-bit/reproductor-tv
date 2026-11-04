/**
 * Dependency-free date/duration formatting helpers.
 *
 * These replace the `dateformat`, `dayjs`, `moment`, and `parse-ms` packages,
 * each of which was used at a single call site for trivial formatting. This is a
 * leaf module that imports nothing else from the project, so it is safe to consume
 * from low-level files (e.g. `Logger`, `Stopwatch`) without creating import cycles.
 */
/**
 * Format a date as `yymmddHHMM` (e.g. `2606291430`).
 * Reproduces `dateformat(date, 'yymmddHHMM')`.
 */
export declare function formatTimestampForPackage(date?: Date): string;
/**
 * Format a date as `YYYY-MM-DD-HH.mm.ss.SSS` (e.g. `2026-06-29-14.30.45.123`).
 * Reproduces `dayjs(date).format('YYYY-MM-DD-HH.mm.ss.SSS')`.
 */
export declare function formatTimestampForScreenshot(date?: Date): string;
/**
 * Format the current time as `hh:mm:ss:SSSS A` (e.g. `02:30:45:1234 PM`).
 * Reproduces `moment(date).format('hh:mm:ss:SSSS A')`.
 *
 * Note: the `SSSS` token in moment renders 4 fractional-second digits by
 * right-padding the millisecond value with a trailing zero.
 */
export declare function formatLogTimestamp(date?: Date): string;
/**
 * Break a non-negative millisecond duration into time components.
 * Reproduces the subset of the `parse-ms` package that `Stopwatch` consumes
 * (durations from `performance.now()` are always non-negative).
 */
export declare function parseMilliseconds(milliseconds: number): {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
    milliseconds: number;
    microseconds: number;
    nanoseconds: number;
};
