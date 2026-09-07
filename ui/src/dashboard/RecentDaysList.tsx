import { formatDayLabel, type DayBucket } from '../lib/days';
import { Card } from '../ui/Card';

interface RecentDaysListProps {
  buckets: DayBucket[];
  limit?: number;
}

export function RecentDaysList({ buckets, limit = 5 }: RecentDaysListProps) {
  const populated = buckets
    .filter((bucket) => bucket.activityCount > 0)
    .slice()
    .sort((a, b) => b.dayKey.localeCompare(a.dayKey))
    .slice(0, limit);

  return (
    <Card title="Recent days">
      {populated.length === 0 ? (
        <p className="empty-note">No days recorded in this window.</p>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {populated.map((bucket) => (
            <li key={bucket.dayKey} className="day-row">
              <span>{formatDayLabel(bucket.dayKey)}</span>
              <span className="day-row-metrics">
                {bucket.steps.toLocaleString()} steps · {bucket.distanceKm.toFixed(1)} km ·{' '}
                {Math.round(bucket.calories).toLocaleString()} kcal
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
