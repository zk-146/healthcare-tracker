import { formatDayLabel, totals, type DayBucket } from '../lib/days';
import { Card } from '../ui/Card';

interface SevenDayChartProps {
  buckets: DayBucket[];
}

export function SevenDayChart({ buckets }: SevenDayChartProps) {
  const window = totals(buckets);
  const peak = Math.max(...buckets.map((b) => b.steps), 1);

  return (
    <Card title="Last 7 days">
      <div className="chart">
        {buckets.map((bucket) => (
          <div
            key={bucket.dayKey}
            className="chart-bar"
            data-testid="chart-bar"
            data-empty={bucket.activityCount === 0}
            style={{ height: `${(bucket.steps / peak) * 100}%` }}
            title={`${formatDayLabel(bucket.dayKey)}: ${bucket.steps.toLocaleString()} steps`}
          />
        ))}
      </div>

      <div className="chart-axis">
        {buckets.map((bucket) => (
          <span key={bucket.dayKey}>{formatDayLabel(bucket.dayKey).charAt(0)}</span>
        ))}
      </div>

      <div className="chart-totals">
        <span>{window.steps.toLocaleString()} steps</span>
        <span>{window.distanceKm.toFixed(1)} km</span>
        <span>{Math.round(window.calories).toLocaleString()} kcal</span>
      </div>
    </Card>
  );
}
