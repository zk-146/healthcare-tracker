import { ApiError } from '../api/client';

export function messageFor(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 429) {
      return 'Too many requests — wait a minute and reload.';
    }
    return cause.body?.error ?? `Request failed (${cause.status})`;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return 'Could not reach the server.';
}
