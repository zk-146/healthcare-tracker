import { nextMilestone } from '../lib/milestones';
import { Card } from '../ui/Card';

interface StreakHeroProps {
  streakDays: number;
}

export function StreakHero({ streakDays }: StreakHeroProps) {
  const next = nextMilestone(streakDays);

  return (
    <Card>
      <b className="streak-value">{streakDays}</b>
      <span className="streak-label">day streak</span>

      {next === null ? (
        <span className="streak-hint">Every milestone earned — keep going.</span>
      ) : (
        <>
          <div
            className="progress"
            role="progressbar"
            aria-valuenow={streakDays}
            aria-valuemin={0}
            aria-valuemax={next.threshold}
            aria-label="Progress to next milestone"
          >
            <span className="progress-fill" style={{ width: `${next.progress * 100}%` }} />
          </div>
          <span className="streak-hint">
            {next.daysRemaining} {next.daysRemaining === 1 ? 'day' : 'days'} to your{' '}
            {next.threshold}-day badge
          </span>
        </>
      )}
    </Card>
  );
}
