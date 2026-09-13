import { describe, expect, it } from 'vitest';
import { defaultRange, validateRange } from './range';

describe('validateRange', () => {
  it('accepts an ordinary range', () => {
    expect(validateRange('2026-08-01', '2026-08-31')).toBeNull();
  });

  it('accepts a single-day range', () => {
    expect(validateRange('2026-08-01', '2026-08-01')).toBeNull();
  });

  it('requires both dates', () => {
    expect(validateRange('', '2026-08-31')).toBe('Pick both a start and end date.');
    expect(validateRange('2026-08-01', '')).toBe('Pick both a start and end date.');
  });

  it('reports a missing date before checking order', () => {
    expect(validateRange('2026-09-30', '')).toBe('Pick both a start and end date.');
  });

  it('rejects a start after the end', () => {
    expect(validateRange('2026-09-02', '2026-09-01')).toBe(
      'Start date must be on or before end date.',
    );
  });

  it('accepts exactly 365 days between the dates', () => {
    expect(validateRange('2025-01-01', '2026-01-01')).toBeNull();
  });

  it('rejects 366 days between the dates', () => {
    expect(validateRange('2025-01-01', '2026-01-02')).toBe("Range can't be longer than 365 days.");
  });

  it('is not thrown off by a DST transition inside the range', () => {
    // Spans both the spring and autumn clock changes in most DST zones.
    expect(validateRange('2025-01-02', '2026-01-02')).toBeNull();
  });
});

describe('defaultRange', () => {
  it('ends today and covers 30 days inclusive', () => {
    expect(defaultRange(new Date(2026, 8, 13, 23, 30))).toEqual({
      from: '2026-08-15',
      to: '2026-09-13',
    });
  });

  it('crosses month and year boundaries', () => {
    expect(defaultRange(new Date(2026, 0, 10))).toEqual({
      from: '2025-12-12',
      to: '2026-01-10',
    });
  });
});
