import { describe, expect, it } from 'vitest';
import type { ProfileResponse } from '../api/types';
import { draftFrom, validateDraft, type ProfileDraft } from './validate';

const now = new Date(2026, 8, 8, 10, 0); // 2026-09-08T10:00 local

const profile: ProfileResponse = {
  id: 'u1',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  dateOfBirth: '1990-01-01',
  gender: 'female',
  heightCm: 170,
  weightKg: 62,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
};

function draft(overrides: Partial<ProfileDraft> = {}): ProfileDraft {
  return { ...draftFrom(profile), ...overrides };
}

describe('draftFrom', () => {
  it('carries the profile fields into string form, blanking absent ones', () => {
    expect(draftFrom({ ...profile, dateOfBirth: null, gender: null, heightCm: null, weightKg: null })).toEqual({
      fullName: 'Ada Lovelace',
      dateOfBirth: '',
      gender: '',
      heightCm: '',
      weightKg: '',
    });
  });

  it('stringifies present numeric fields', () => {
    expect(draftFrom(profile)).toEqual({
      fullName: 'Ada Lovelace',
      dateOfBirth: '1990-01-01',
      gender: 'female',
      heightCm: '170',
      weightKg: '62',
    });
  });
});

describe('validateDraft', () => {
  it('accepts a fully-filled draft', () => {
    const { errors, input } = validateDraft(draft(), now);
    expect(errors).toEqual({});
    expect(input).toEqual({
      fullName: 'Ada Lovelace',
      dateOfBirth: '1990-01-01',
      gender: 'female',
      heightCm: 170,
      weightKg: 62,
    });
  });

  it('omits blank optional fields from the input rather than rejecting them', () => {
    const { errors, input } = validateDraft(
      draft({ dateOfBirth: '', gender: '', heightCm: '', weightKg: '' }),
      now,
    );
    expect(errors).toEqual({});
    expect(input).toEqual({ fullName: 'Ada Lovelace' });
  });

  it('requires a non-blank full name', () => {
    const { errors, input } = validateDraft(draft({ fullName: '   ' }), now);
    expect(errors.fullName).toMatch(/required/i);
    expect(input).toBeNull();
  });

  it('rejects a full name over 150 characters', () => {
    const { errors, input } = validateDraft(draft({ fullName: 'x'.repeat(151) }), now);
    expect(errors.fullName).toMatch(/150 characters/);
    expect(input).toBeNull();
  });

  it('rejects a date of birth that is not in the past', () => {
    const { errors, input } = validateDraft(draft({ dateOfBirth: '2030-01-01' }), now);
    expect(errors.dateOfBirth).toMatch(/past/i);
    expect(input).toBeNull();
  });

  it('rejects a date of birth of today (local time, not UTC midnight)', () => {
    const { errors, input } = validateDraft(draft({ dateOfBirth: '2026-09-08' }), now);
    expect(errors.dateOfBirth).toMatch(/past/i);
    expect(input).toBeNull();
  });

  it('rejects gender text over 20 characters', () => {
    const { errors, input } = validateDraft(draft({ gender: 'x'.repeat(21) }), now);
    expect(errors.gender).toMatch(/20 characters/);
    expect(input).toBeNull();
  });

  it.each([
    ['heightCm', '0', /0\.1 and 300/],
    ['heightCm', '301', /0\.1 and 300/],
    ['weightKg', '0', /0\.1 and 700/],
    ['weightKg', '701', /0\.1 and 700/],
  ] as const)('rejects an out-of-range %s value of %s', (field, value, message) => {
    const { errors, input } = validateDraft(draft({ [field]: value }), now);
    expect(errors[field]).toMatch(message);
    expect(input).toBeNull();
  });
});
