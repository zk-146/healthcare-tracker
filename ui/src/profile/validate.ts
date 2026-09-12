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

/** validateDraft's default `original` when the caller has no baseline to compare
 *  against (e.g. existing tests) — every field reads as "never set", so the
 *  can't-clear guard below never fires for them. */
const NO_BASELINE: ProfileDraft = {
  fullName: '',
  dateOfBirth: '',
  gender: '',
  heightCm: '',
  weightKg: '',
};

/**
 * The backend's PUT is a partial update: an omitted field is left unchanged, and
 * there is no way to send "clear this field back to null" (see ProfileUpdateInput's
 * doc comment). Blanking an optional field that currently has a value would
 * therefore silently no-op — the save "succeeds", the server's response still
 * carries the old value, and the form reverts to it right under a "Saved." message
 * with no explanation. Rejecting the attempt up front, with a clear message, is far
 * better than that silent round-trip.
 */
const CANNOT_CLEAR_MESSAGE = "Can't be cleared here once set — enter a new value instead.";

/**
 * Validates and narrows a draft to a ProfileUpdateInput. Every field is optional on
 * the wire (a partial update), so an empty field is simply omitted rather than
 * rejected — except fullName, which the backend requires to be non-blank whenever a
 * profile exists at all, and the four fields below when `original` shows they were
 * previously set (see CANNOT_CLEAR_MESSAGE).
 */
export function validateDraft(
  draft: ProfileDraft,
  now: Date,
  original: ProfileDraft = NO_BASELINE,
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
  if (draft.dateOfBirth.trim() === '') {
    if (original.dateOfBirth.trim() !== '') {
      errors.dateOfBirth = CANNOT_CLEAR_MESSAGE;
    }
  } else if (new Date(`${draft.dateOfBirth}T00:00:00`) >= today) {
    errors.dateOfBirth = 'Date of birth must be in the past';
  }

  const gender = draft.gender.trim();
  if (gender === '') {
    if (original.gender.trim() !== '') {
      errors.gender = CANNOT_CLEAR_MESSAGE;
    }
  } else if (gender.length > 20) {
    errors.gender = 'Gender must not exceed 20 characters';
  }

  const optional: Record<string, number> = {};
  for (const key of ['heightCm', 'weightKg'] as const) {
    const raw = draft[key];
    if (raw.trim() === '') {
      if (original[key].trim() !== '') {
        errors[key] = CANNOT_CLEAR_MESSAGE;
      }
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
