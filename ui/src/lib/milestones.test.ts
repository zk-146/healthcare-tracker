import { describe, expect, it } from 'vitest';
import { MILESTONE_THRESHOLDS, nextMilestone } from './milestones';

describe('nextMilestone', () => {
  it('returns the first threshold above the current streak', () => {
    expect(nextMilestone(0)?.threshold).toBe(3);
    expect(nextMilestone(12)?.threshold).toBe(14);
  });

  it('advances past a threshold the streak has already reached', () => {
    expect(nextMilestone(3)?.threshold).toBe(7);
  });

  it('reports days remaining and fractional progress', () => {
    const next = nextMilestone(12);
    expect(next?.daysRemaining).toBe(2);
    expect(next?.progress).toBeCloseTo(12 / 14);
  });

  it('returns null once every threshold is passed', () => {
    expect(nextMilestone(365)).toBeNull();
    expect(nextMilestone(400)).toBeNull();
  });

  it('exposes the ladder in ascending order', () => {
    expect([...MILESTONE_THRESHOLDS]).toEqual([3, 7, 14, 30, 60, 100, 365]);
  });
});
