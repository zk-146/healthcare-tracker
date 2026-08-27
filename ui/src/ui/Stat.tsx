interface StatProps {
  value: string;
  label: string;
}

export function Stat({ value, label }: StatProps) {
  return (
    <div className="stat">
      <b className="stat-value">{value}</b>
      <span className="stat-label">{label}</span>
    </div>
  );
}
