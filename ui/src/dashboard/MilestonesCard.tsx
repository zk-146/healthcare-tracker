import { type ApiClient } from '../api/client';
import { getMilestones } from '../api/endpoints';
import type { MilestoneResponse } from '../api/types';
import { dayKeyOf, formatDayLabel } from '../lib/days';
import { MILESTONE_THRESHOLDS } from '../lib/milestones';
import { useLoadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';

interface MilestonesCardProps {
  api: ApiClient;
}

/**
 * The full ladder always renders, earned or not — seeing "100" greyed out next to
 * an earned "30" is the point: it shows the user what is still ahead, not just a
 * shrinking trophy case.
 */
export function MilestonesCard({ api }: MilestonesCardProps) {
  const milestones = useLoadable<MilestoneResponse[]>(() => getMilestones(api), [api]);

  return (
    <Card title="Milestones">
      {milestones.state === 'loading' && <Skeleton height={90} />}
      {milestones.state === 'error' && <ErrorNote message={milestones.message} />}
      {milestones.state === 'ready' && (
        <ul className="milestone-list">
          {MILESTONE_THRESHOLDS.map((threshold) => {
            const earned = milestones.value.find((m) => m.milestoneDays === threshold) ?? null;
            return (
              <li key={threshold} className="milestone-badge" data-earned={earned !== null}>
                <span className="milestone-days">{threshold}</span>
                <span className="milestone-label">
                  {earned === null ? 'Not yet' : formatDayLabel(dayKeyOf(earned.achievedAt))}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
