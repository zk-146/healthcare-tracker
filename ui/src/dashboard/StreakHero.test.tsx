import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StreakHero } from './StreakHero';

describe('StreakHero', () => {
  it('shows the streak and the days remaining to the next badge', () => {
    render(<StreakHero streakDays={12} />);
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText(/2 days to your 14-day badge/i)).toBeInTheDocument();
  });

  it('uses singular wording when one day remains', () => {
    render(<StreakHero streakDays={13} />);
    expect(screen.getByText(/1 day to your 14-day badge/i)).toBeInTheDocument();
  });

  it('encourages a restart when the streak is zero', () => {
    render(<StreakHero streakDays={0} />);
    expect(screen.getByText('0')).toBeInTheDocument();
    expect(screen.getByText(/3 days to your 3-day badge/i)).toBeInTheDocument();
  });

  it('celebrates rather than erroring once every milestone is passed', () => {
    render(<StreakHero streakDays={400} />);
    expect(screen.getByText(/every milestone earned/i)).toBeInTheDocument();
  });

  it('exposes progress to assistive technology', () => {
    render(<StreakHero streakDays={12} />);
    const bar = screen.getByRole('progressbar');
    expect(bar).toHaveAttribute('aria-valuenow', '12');
    expect(bar).toHaveAttribute('aria-valuemax', '14');
  });
});
