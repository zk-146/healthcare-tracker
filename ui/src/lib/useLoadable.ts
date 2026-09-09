import { useEffect, useState } from 'react';
import { messageFor } from './apiMessage';

export type Loadable<T> =
  | { state: 'loading' }
  | { state: 'ready'; value: T }
  | { state: 'error'; message: string };

export function useLoadable<T>(load: () => Promise<T>, deps: unknown[]): Loadable<T> {
  const [result, setResult] = useState<Loadable<T>>({ state: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setResult({ state: 'loading' });
    load()
      .then((value) => {
        if (!cancelled) {
          setResult({ state: 'ready', value });
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled) {
          setResult({ state: 'error', message: messageFor(cause) });
        }
      });
    return () => {
      cancelled = true;
    };
    // `deps` is passed through deliberately: each caller controls its own invalidation.
  }, deps);

  return result;
}
