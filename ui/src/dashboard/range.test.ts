import { describe, expect, it } from 'vitest';
import { defaultRange, validateRange } from './range';

const TODAY = '2026-09-13';

describe('validateRange', () => {
  it('accepts an ordinary range', () => {
    expect(validateRange('2026-08-01', '2026-08-31', TODAY)).toBeNull();
  });

  it('accepts a single-day range', () => {
    expect(validateRange('2026-08-01', '2026-08-01', TODAY)).toBeNull();
  });

  it('requires both dates', () => {
    expect(validateRange('', '2026-08-31', TODAY)).toBe('Pick both a start and end date.');
    expect(validateRange('2026-08-01', '', TODAY)).toBe('Pick both a start and end date.');
  });

  it('reports a missing date before checking order', () => {
    expect(validateRange('2026-09-30', '', TODAY)).toBe('Pick both a start and end date.');
  });

  it('rejects a start after the end', () => {
    expect(validateRange('2026-09-02', '2026-09-01', TODAY)).toBe(
      'Start date must be on or before end date.',
    );
  });

  it('accepts exactly 365 days between the dates', () => {
    expect(validateRange('2025-01-01', '2026-01-01', TODAY)).toBeNull();
  });

  it('rejects 366 days between the dates', () => {
    expect(validateRange('2025-01-01', '2026-01-02', TODAY)).toBe("Range can't be longer than 365 days.");
  });

  it('accepts a range ending today', () => {
    expect(validateRange('2026-09-01', TODAY, TODAY)).toBeNull();
  });

  it('rejects an end date in the future', () => {
    expect(validateRange('2026-09-01', '2026-09-14', TODAY)).toBe(
      "End date can't be in the future.",
    );
  });

  it('reports a reversed range before a future end date', () => {
    expect(validateRange('2026-09-20', '2026-09-14', TODAY)).toBe(
      'Start date must be on or before end date.',
    );
  });

  it('is not thrown off by a DST transition inside the range', () => {
    // Spans both the spring and autumn clock changes in most DST zones.
    expect(validateRange('2025-01-02', '2026-01-02', TODAY)).toBeNull();
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
