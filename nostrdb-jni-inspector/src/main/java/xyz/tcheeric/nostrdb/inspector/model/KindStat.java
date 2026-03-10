package xyz.tcheeric.nostrdb.inspector.model;

/**
 * Statistics for a single event kind.
 *
 * @param kind     Kind number
 * @param label    Human-readable label
 * @param count    Number of events
 * @param dataSize Pre-formatted data size string
 */
public record KindStat(int kind, String label, long count, String dataSize) {}
