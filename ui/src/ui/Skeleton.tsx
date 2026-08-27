interface SkeletonProps {
  height?: number;
}

/** Occupies the card's eventual height so layout does not shift as data arrives. */
export function Skeleton({ height = 64 }: SkeletonProps) {
  return <div className="skeleton" aria-hidden="true" style={{ height }} />;
}
