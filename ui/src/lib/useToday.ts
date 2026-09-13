import { useEffect, useState } from 'react';
import { toDayKey } from './days';

/**
 * Today's local day key, updated at local midnight. Computing `toDayKey(new Date())`
 * during render only refreshes when something else re-renders, so a page left idle
 * overnight would keep capping its date pickers at yesterday.
 */
export function useToday(): string {
  const [today, setToday] = useState(() => toDayKey(new Date()));

  useEffect(() => {
    const now = new Date();
    const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    // A second past midnight, so the callback lands inside the new day. If the machine
    // slept through midnight the timer fires late, and new Date() is still correct then.
    const id = setTimeout(
      () => setToday(toDayKey(new Date())),
      nextMidnight.getTime() - now.getTime() + 1000,
    );
    return () => clearTimeout(id);
  }, [today]);

  return today;
}
