import type { ProfileResponse, ProfileUpdateInput } from '../api/types';

/** The form's own shape: every editable field a string, since that is what DOM inputs hold. */
export interface ProfileDraft {
  fullName: string;
  /** date input value, "YYYY-MM-DD", or '' when unset. */
  dateOfBirth: string;
  gender: string;
  heightCm: string;
  weightKg: string;
}

export type FieldErrors = Partial<Record<keyof ProfileDraft, string>>;

export function draftFrom(profile: ProfileResponse): ProfileDraft {
  return {
    fullName: profile.fullName,
    dateOfBirth: profile.dateOfBirth ?? '',
    gender: profile.gender ?? '',
    heightCm: profile.heightCm === null ? '' : String(profile.heightCm),
    weightKg: profile.weightKg === null ? '' : String(profile.weightKg),
  };
}

interface NumericRule {
  message: string;
  min: number;
  max: number;
}

/** Mirrors ProfileUpdateRequest's @DecimalMin/@DecimalMax bounds. */
const NUMERIC_RULES: Record<'heightCm' | 'weightKg', NumericRule> = {
  heightCm: { message: 'Height must be between 0.1 and 300 cm', min: 0.1, max: 300 },
  weightKg: { message: 'Weight must be between 0.1 and 700 kg', min: 0.1, max: 700 },
};

function parseNumeric(raw: string, rule: NumericRule): number | null {
  const value = Number(raw);
  if (raw.trim() === '' || Number.isNaN(value) || value < rule.min || value > rule.max) {
    return null;
  }
  return value;
}

/**
 * Validates and narrows a draft to a ProfileUpdateInput. Every field is optional on
 * the wire (a partial update), so an empty field is simply omitted rather than
 * rejected — except fullName, which the backend requires to be non-blank whenever a
 * profile exists at all.
 */
export function validateDraft(
  draft: ProfileDraft,
  now: Date,
): { errors: FieldErrors; input: ProfileUpdateInput | null } {
  const errors: FieldErrors = {};
  const fullName = draft.fullName.trim();

  if (fullName === '') {
    errors.fullName = 'Full name is required';
  } else if (fullName.length > 150) {
    errors.fullName = 'Full name cannot exceed 150 characters';
  }

  // The backend's @Past on a LocalDate compares whole dates, not instants — today's date
  // is rejected regardless of time of day. Match that here: zero out `now`'s time before
  // comparing, and parse dateOfBirth with a local (not UTC-midnight) time-of-day, same as
  // WorkoutForm's startedAt.
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (draft.dateOfBirth.trim() !== '' && new Date(`${draft.dateOfBirth}T00:00:00`) >= today) {
    errors.dateOfBirth = 'Date of birth must be in the past';
  }

  const gender = draft.gender.trim();
  if (gender.length > 20) {
    errors.gender = 'Gender must not exceed 20 characters';
  }

  const optional: Record<string, number> = {};
  for (const key of ['heightCm', 'weightKg'] as const) {
    const raw = draft[key];
    if (raw.trim() === '') {
      continue;
    }
    const value = parseNumeric(raw, NUMERIC_RULES[key]);
    if (value === null) {
      errors[key] = NUMERIC_RULES[key].message;
    } else {
      optional[key] = value;
    }
  }

  if (Object.keys(errors).length > 0) {
    return { errors, input: null };
  }

  return {
    errors,
    input: {
      fullName,
      ...(draft.dateOfBirth.trim() !== '' ? { dateOfBirth: draft.dateOfBirth } : {}),
      ...(gender !== '' ? { gender } : {}),
      ...optional,
    },
  };
}
