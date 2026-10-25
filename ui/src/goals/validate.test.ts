import { describe, expect, it } from 'vitest';
import { emptyDraft, targetError, validateDraft, type GoalDraft } from './validate';

function draft(overrides: Partial<GoalDraft> = {}): GoalDraft {
  return { ...emptyDraft(), ...overrides };
}

describe('emptyDraft', () => {
  it('defaults to a weekly goal', () => {
    // Weekly by design: a daily target is as unforgiving as the streak this is meant to soften.
    expect(emptyDraft().period).toBe('WEEKLY');
    expect(emptyDraft().targetValue).toBe('');
  });
});

describe('targetError', () => {
  it('accepts a target inside the ceiling', () => {
    expect(targetError('150', 'MINUTES', 'WEEKLY')).toBeNull();
  });

  it('requires a value', () => {
    expect(targetError('', 'MINUTES', 'WEEKLY')).toBe('Target is required');
    expect(targetError('   ', 'MINUTES', 'WEEKLY')).toBe('Target is required');
  });

  it('rejects non-numeric input', () => {
    expect(targetError('abc', 'MINUTES', 'WEEKLY')).toBe('Target must be a number');
  });

  it('rejects zero and negatives', () => {
    expect(targetError('0', 'MINUTES', 'WEEKLY')).toBe('Target must be greater than 0');
    expect(targetError('-5', 'MINUTES', 'WEEKLY')).toBe('Target must be greater than 0');
  });

  it('rejects fractions for whole-unit metrics', () => {
    expect(targetError('3.5', 'SESSIONS', 'WEEKLY')).toMatch(/whole number of sessions/);
    expect(targetError('5000.5', 'STEPS', 'DAILY')).toMatch(/whole number of steps/);
  });

  it('allows fractions for distance and calories', () => {
    expect(targetError('12.5', 'DISTANCE_KM', 'WEEKLY')).toBeNull();
    expect(targetError('1750.5', 'CALORIES', 'WEEKLY')).toBeNull();
  });

  it('enforces the ceiling at the period scale', () => {
    expect(targetError('10080', 'MINUTES', 'WEEKLY')).toBeNull();
    expect(targetError('10081', 'MINUTES', 'WEEKLY')).toMatch(/cannot exceed/);

    // Same number, two verdicts — which is the reason the bound is scaled by period.
    expect(targetError('500000', 'STEPS', 'MONTHLY')).toBeNull();
    expect(targetError('500000', 'STEPS', 'DAILY')).toMatch(/cannot exceed/);
  });

  it('names the unit and period in the ceiling message', () => {
    const message = targetError('999999', 'DISTANCE_KM', 'WEEKLY');

    expect(message).toContain('7,000');
    expect(message).toContain('km');
    expect(message).toContain('weekly');
  });
});

describe('validateDraft', () => {
  it('converts a valid draft into a GoalInput with a numeric target', () => {
    const { errors, input } = validateDraft(
      draft({ metric: 'DISTANCE_KM', period: 'WEEKLY', targetValue: '20.5' }),
    );

    expect(errors).toEqual({});
    expect(input).toEqual({ metric: 'DISTANCE_KM', period: 'WEEKLY', targetValue: 20.5 });
  });

  it('returns a null input and a keyed error when the target is bad', () => {
    const { errors, input } = validateDraft(draft({ targetValue: '0' }));

    expect(input).toBeNull();
    expect(errors.targetValue).toBe('Target must be greater than 0');
  });
});
