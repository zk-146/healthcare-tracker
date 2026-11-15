import '@testing-library/jest-dom/vitest';

/**
 * Node 25+ defines its own global `localStorage` and `sessionStorage`. Without
 * `--localstorage-file` they evaluate to undefined, and because they already exist on the
 * global object, Vitest's jsdom environment does not replace them with jsdom's — so
 * `window.localStorage` is undefined in tests even though jsdom has a working one.
 *
 * Put jsdom's implementations back when that has happened. On Node versions without the
 * built-in (22 and earlier) the globals are already jsdom's and this does nothing.
 */
const jsdomWindow = (globalThis as { jsdom?: { window: Window } }).jsdom?.window;
if (jsdomWindow !== undefined) {
  for (const name of ['localStorage', 'sessionStorage'] as const) {
    if (globalThis[name] === undefined) {
      Object.defineProperty(globalThis, name, {
        value: jsdomWindow[name],
        configurable: true,
        writable: true,
      });
    }
  }
}
