import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Card } from './Card';
import { ErrorNote } from './ErrorNote';
import { Skeleton } from './Skeleton';
import { Stat } from './Stat';

describe('primitives', () => {
  it('Card renders its title as a heading and shows children', () => {
    render(<Card title="Today">contents</Card>);
    expect(screen.getByRole('heading', { name: 'Today' })).toBeInTheDocument();
    expect(screen.getByText('contents')).toBeInTheDocument();
  });

  it('Card omits the heading when no title is given', () => {
    render(<Card>contents</Card>);
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('Stat shows a value and its label', () => {
    render(<Stat value="6,307" label="steps" />);
    expect(screen.getByText('6,307')).toBeInTheDocument();
    expect(screen.getByText('steps')).toBeInTheDocument();
  });

  it('Skeleton is hidden from assistive technology', () => {
    const { container } = render(<Skeleton />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
  });

  it('ErrorNote announces itself as an alert', () => {
    render(<ErrorNote message="Could not load" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load');
  });
});
