import { formatDayLabel, type DayBucket } from '../lib/days';
import { Card } from '../ui/Card';
import { Stat } from '../ui/Stat';

interface LatestDayCardProps {
  bucket: DayBucket | null;
}

export function LatestDayCard({ bucket }: LatestDayCardProps) {
  if (bucket === null) {
    return (
      <Card title="Latest day">
        <p className="empty-note">
          No activity data yet. Import a Fitbit CSV to get started.
        </p>
      </Card>
    );
  }

  return (
    <Card title={formatDayLabel(bucket.dayKey)}>
      <div className="stats-row">
        <Stat value={bucket.steps.toLocaleString()} label="steps" />
        <Stat value={bucket.distanceKm.toFixed(1)} label="km" />
        <Stat value={Math.round(bucket.calories).toLocaleString()} label="total burn" />
      </div>
    </Card>
  );
}
