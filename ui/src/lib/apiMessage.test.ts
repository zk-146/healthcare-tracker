import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { messageFor } from './apiMessage';

describe('messageFor', () => {
  it('uses the error body message from an ApiError', () => {
    expect(messageFor(new ApiError(400, { error: 'Duration must be at least 1 minute' })))
      .toBe('Duration must be at least 1 minute');
  });

  it('special-cases 429', () => {
    expect(messageFor(new ApiError(429, null))).toBe('Too many requests — wait a minute and reload.');
  });

  it('falls back to the status when the body has no error text', () => {
    expect(messageFor(new ApiError(500, null))).toBe('Request failed (500)');
  });

  it('uses the message of a plain Error', () => {
    expect(messageFor(new Error('boom'))).toBe('boom');
  });

  it('has a catch-all for non-Error throws', () => {
    expect(messageFor('nope')).toBe('Could not reach the server.');
  });
});
