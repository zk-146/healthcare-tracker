import { describe, expect, it } from 'vitest';
import type { GoalMetric, GoalProgress, GoalResponse } from '../api/types';
import { barFraction, formatValue, goalTitle, maxTarget, metricUnit, paceNote } from './goals';

/** Overrides allow a partial progress block so each test states only the fields it cares about. */
type GoalOverrides = Partial<Omit<GoalResponse, 'progress'>> & {
  progress?: Partial<GoalProgress>;
};

function goal(overrides: GoalOverrides = {}): GoalResponse {
  const base: GoalResponse = {
    id: 'g1',
    metric: 'MINUTES',
    period: 'WEEKLY',
    targetValue: 150,
    unit: 'minutes',
    createdAt: '2026-09-01T10:00:00',
    updatedAt: '2026-09-01T10:00:00',
    progress: {
      from: '2026-09-07',
      to: '2026-09-09',
      currentValue: 60,
      percentComplete: 40,
      achieved: false,
      daysElapsed: 3,
      daysTotal: 7,
      daysRemaining: 4,
      expectedByNow: 64.3,
      onTrack: false,
    },
  };
  return { ...base, ...overrides, progress: { ...base.progress, ...overrides.progress } };
}

describe('maxTarget', () => {
  it('scales the per-day ceiling by period length, mirroring GoalMetric', () => {
    expect(maxTarget('MINUTES', 'DAILY')).toBe(1_440);
    expect(maxTarget('MINUTES', 'WEEKLY')).toBe(10_080);
    expect(maxTarget('MINUTES', 'MONTHLY')).toBe(44_640);
    expect(maxTarget('STEPS', 'WEEKLY')).toBe(700_000);
    expect(maxTarget('SESSIONS', 'DAILY')).toBe(24);
  });
});

describe('formatValue', () => {
  it('renders whole metrics with separators and no decimals', () => {
    expect(formatValue(70_000, 'STEPS')).toBe('70,000');
    expect(formatValue(149.6, 'MINUTES')).toBe('150');
  });

  it('keeps one decimal for fractional metrics, dropping a trailing zero', () => {
    expect(formatValue(12.4, 'DISTANCE_KM')).toBe('12.4');
    expect(formatValue(8, 'DISTANCE_KM')).toBe('8');
    expect(formatValue(1_750.5, 'CALORIES')).toBe('1,750.5');
  });
});

describe('goalTitle', () => {
  it('reads as a sentence fragment', () => {
    expect(goalTitle('MINUTES', 'WEEKLY')).toBe('Weekly active minutes');
    expect(goalTitle('STEPS', 'DAILY')).toBe('Daily steps');
    expect(goalTitle('SESSIONS', 'MONTHLY')).toBe('Monthly workouts');
  });
});

describe('metricUnit', () => {
  it('covers every metric so the create form never renders an empty suffix', () => {
    const metrics: GoalMetric[] = ['MINUTES', 'SESSIONS', 'DISTANCE_KM', 'STEPS', 'CALORIES'];
    for (const metric of metrics) {
      expect(metricUnit(metric)).not.toBe('');
    }
  });
});

describe('barFraction', () => {
  it('clamps to 1 so an exceeded goal does not overflow the bar', () => {
    expect(barFraction(goal({ progress: { percentComplete: 150 } }))).toBe(1);
  });

  it('clamps negatives to 0', () => {
    expect(barFraction(goal({ progress: { percentComplete: -5 } }))).toBe(0);
  });

  it('passes a normal value through as a fraction', () => {
    expect(barFraction(goal({ progress: { percentComplete: 40 } }))).toBeCloseTo(0.4);
  });
});

describe('paceNote', () => {
  it('reports the shortfall against an even pace when behind', () => {
    // Behind by 64.3 - 60 = 4.3, rounded to a whole minute. 90 still to go.
    expect(paceNote(goal())).toBe('4 minutes behind pace, 90 to go with 4 days left.');
  });

  it('reports what is left to go when on track', () => {
    const note = paceNote(
      goal({ progress: { currentValue: 70, percentComplete: 46.7, onTrack: true } }),
    );

    expect(note).toBe('On track, 80 minutes to go with 4 days left.');
  });

  it('celebrates a met goal rather than reporting pace', () => {
    const note = paceNote(
      goal({
        progress: { currentValue: 150, percentComplete: 100, achieved: true, onTrack: true },
      }),
    );

    expect(note).toBe('Goal met — 150 of 150 minutes.');
  });

  it('still celebrates when the goal is exceeded', () => {
    const note = paceNote(
      goal({
        progress: { currentValue: 225, percentComplete: 150, achieved: true, onTrack: true },
      }),
    );

    expect(note).toBe('Goal met — 225 of 150 minutes.');
  });

  it('says "on the last day" rather than "0 days left"', () => {
    const note = paceNote(
      goal({
        progress: {
          currentValue: 100,
          daysElapsed: 7,
          daysRemaining: 0,
          expectedByNow: 150,
          onTrack: false,
        },
      }),
    );

    expect(note).toBe('50 minutes behind pace, 50 to go on the last day.');
  });

  it('uses the singular day', () => {
    const note = paceNote(
      goal({ progress: { currentValue: 140, daysRemaining: 1, onTrack: true } }),
    );

    expect(note).toBe('On track, 10 minutes to go with 1 day left.');
  });

  it('never reports a negative amount still to go', () => {
    // A goal exceeded but somehow not flagged achieved must not read "-20 to go".
    const note = paceNote(
      goal({ progress: { currentValue: 170, percentComplete: 113, onTrack: true } }),
    );

    expect(note).toBe('On track, 0 minutes to go with 4 days left.');
  });
});
