import { describe, expect, it } from 'vitest';
import type { ActivityResponse } from '../api/types';
import {
  draftFrom,
  emptyDraft,
  hasDetails,
  toLocalInput,
  validateDraft,
  type WorkoutDraft,
} from './validate';

const now = new Date(2026, 8, 8, 10, 0); // 2026-09-08T10:00 local

function draft(overrides: Partial<WorkoutDraft> = {}): WorkoutDraft {
  return { ...emptyDraft(now), durationMinutes: '30', ...overrides };
}

describe('toLocalInput', () => {
  it('formats a Date as a zero-padded datetime-local value', () => {
    expect(toLocalInput(new Date(2026, 0, 3, 7, 5))).toBe('2026-01-03T07:05');
  });
});

describe('emptyDraft', () => {
  it('defaults the type to WALKING, starts at now, and leaves the rest blank', () => {
    expect(emptyDraft(now)).toEqual({
      activityType: 'WALKING',
      startedAt: '2026-09-08T10:00',
      durationMinutes: '',
      distanceKm: '',
      caloriesBurned: '',
      steps: '',
      heartRateAvg: '',
      notes: '',
    });
  });
});

describe('draftFrom', () => {
  const activity: ActivityResponse = {
    id: 'a1',
    activityType: 'CYCLING',
    source: 'MANUAL',
    deviceId: null,
    startedAt: '2026-09-07T18:15:00',
    endedAt: null,
    durationMinutes: 50,
    distanceKm: 18.4,
    caloriesBurned: null,
    heartRateAvg: null,
    steps: null,
    notes: 'hill route',
    createdAt: '2026-09-07T19:10:00',
    updatedAt: '2026-09-07T19:10:00',
  };

  it('trims the seconds off startedAt and blanks the null fields', () => {
    expect(draftFrom(activity)).toEqual({
      activityType: 'CYCLING',
      startedAt: '2026-09-07T18:15',
      durationMinutes: '50',
      distanceKm: '18.4',
      caloriesBurned: '',
      steps: '',
      heartRateAvg: '',
      notes: 'hill route',
    });
  });
});

describe('hasDetails', () => {
  it('is false when every optional field is blank', () => {
    expect(hasDetails(draft())).toBe(false);
  });

  it('is true when any optional field is set', () => {
    expect(hasDetails(draft({ notes: 'x' }))).toBe(true);
    expect(hasDetails(draft({ steps: '10' }))).toBe(true);
  });
});

describe('validateDraft', () => {
  it('accepts a minimal draft and appends seconds to startedAt', () => {
    const { errors, input } = validateDraft(draft(), now);

    expect(errors).toEqual({});
    expect(input).toEqual({
      activityType: 'WALKING',
      startedAt: '2026-09-08T10:00:00',
      durationMinutes: 30,
    });
  });

  it('omits blank optional fields rather than sending nulls', () => {
    const { input } = validateDraft(draft({ steps: '4200', notes: '  ' }), now);

    expect(input).toEqual({
      activityType: 'WALKING',
      startedAt: '2026-09-08T10:00:00',
      durationMinutes: 30,
      steps: 4200,
    });
  });

  it('requires a start time', () => {
    const { errors, input } = validateDraft(draft({ startedAt: '' }), now);

    expect(errors.startedAt).toBe('Start time is required');
    expect(input).toBeNull();
  });

  it('rejects a start time in the future', () => {
    const { errors } = validateDraft(draft({ startedAt: '2026-09-08T10:01' }), now);

    expect(errors.startedAt).toBe('Start time cannot be in the future');
  });

  it('requires a duration', () => {
    expect(validateDraft(draft({ durationMinutes: '' }), now).errors.durationMinutes).toBe(
      'Duration is required',
    );
  });

  it('holds duration to 1-1440 minutes', () => {
    expect(validateDraft(draft({ durationMinutes: '0' }), now).errors.durationMinutes).toBe(
      'Duration must be between 1 and 1440 minutes',
    );
    expect(validateDraft(draft({ durationMinutes: '1441' }), now).errors.durationMinutes).toBe(
      'Duration must be between 1 and 1440 minutes',
    );
    expect(validateDraft(draft({ durationMinutes: '1440' }), now).errors).toEqual({});
  });

  it('holds distance to 0 exclusive - 1000 km', () => {
    expect(validateDraft(draft({ distanceKm: '0' }), now).errors.distanceKm).toBe(
      'Distance must be between 0 and 1000 km',
    );
    expect(validateDraft(draft({ distanceKm: '1000.1' }), now).errors.distanceKm).toBe(
      'Distance must be between 0 and 1000 km',
    );
    expect(validateDraft(draft({ distanceKm: '0.1' }), now).errors).toEqual({});
  });

  it('holds calories to 0 exclusive - 10000', () => {
    expect(validateDraft(draft({ caloriesBurned: '10001' }), now).errors.caloriesBurned).toBe(
      'Calories must be between 0 and 10000',
    );
  });

  it('holds steps to 1-100000 whole numbers', () => {
    expect(validateDraft(draft({ steps: '100001' }), now).errors.steps).toBe(
      'Steps must be a whole number between 1 and 100000',
    );
    expect(validateDraft(draft({ steps: '12.5' }), now).errors.steps).toBe(
      'Steps must be a whole number between 1 and 100000',
    );
  });

  it('holds heart rate to 1-300 bpm', () => {
    expect(validateDraft(draft({ heartRateAvg: '301' }), now).errors.heartRateAvg).toBe(
      'Heart rate must be a whole number between 1 and 300 bpm',
    );
  });

  it('rejects notes longer than 1000 characters', () => {
    expect(validateDraft(draft({ notes: 'x'.repeat(1001) }), now).errors.notes).toBe(
      'Notes cannot exceed 1000 characters',
    );
  });

  it('rejects a non-numeric entry', () => {
    expect(validateDraft(draft({ distanceKm: 'five' }), now).errors.distanceKm).toBe(
      'Distance must be between 0 and 1000 km',
    );
  });

  it('reports every bad field at once', () => {
    const { errors } = validateDraft(draft({ durationMinutes: '0', steps: '0' }), now);

    expect(Object.keys(errors).sort()).toEqual(['durationMinutes', 'steps']);
  });
});
