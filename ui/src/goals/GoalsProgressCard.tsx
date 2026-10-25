import { type ApiClient } from '../api/client';
import { listGoals } from '../api/endpoints';
import type { GoalResponse } from '../api/types';
import { barFraction, goalTitle, formatValue, paceNote } from '../lib/goals';
import { useLoadable } from '../lib/useLoadable';
import { Card } from '../ui/Card';
import { ErrorNote } from '../ui/ErrorNote';
import { Skeleton } from '../ui/Skeleton';

interface GoalsProgressCardProps {
  api: ApiClient;
}

function GoalRow({ goal }: { goal: GoalResponse }) {
  const { progress, metric, unit } = goal;
  const title = goalTitle(metric, goal.period);

  return (
    <li className="goal-row" data-on-track={progress.onTrack}>
      <div className="goal-row-head">
        <span className="goal-row-title">{title}</span>
        <span className="goal-row-value">
          {formatValue(progress.currentValue, metric)} / {formatValue(goal.targetValue, metric)}{' '}
          {unit}
        </span>
      </div>

      {/* Same markup and classes as StreakHero's bar — the styles already exist in styles.css. */}
      <div
        className="progress"
        role="progressbar"
        aria-valuenow={Math.round(progress.currentValue)}
        aria-valuemin={0}
        aria-valuemax={Math.round(goal.targetValue)}
        aria-label={`Progress toward ${title} goal`}
      >
        <span className="progress-fill" style={{ width: `${barFraction(goal) * 100}%` }} />
      </div>

      <span className="goal-row-note">{paceNote(goal)}</span>
    </li>
  );
}

/**
 * Read-only standing against each goal, shown directly beneath the streak hero.
 *
 * The placement is the point: a streak reports a single unforgiving fact, and this sits next to
 * it reporting a forgiving one. A user who misses a day loses the streak but keeps most of their
 * weekly progress, and both are visible at once.
 *
 * Renders nothing at all when no goals are set, rather than an empty frame — the Profile card is
 * where goals get created, and an empty card here would just be dead space on the dashboard.
 */
export function GoalsProgressCard({ api }: GoalsProgressCardProps) {
  const goals = useLoadable<GoalResponse[]>(() => listGoals(api), [api]);

  if (goals.state === 'loading') {
    return <Skeleton height={120} />;
  }

  if (goals.state === 'error') {
    return <ErrorNote message={goals.message} />;
  }

  if (goals.value.length === 0) {
    return null;
  }

  return (
    <Card title="Goals">
      <ul className="goal-list">
        {goals.value.map((goal) => (
          <GoalRow key={goal.id} goal={goal} />
        ))}
      </ul>
    </Card>
  );
}
