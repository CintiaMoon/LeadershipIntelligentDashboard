import { describe, expect, it } from 'vitest';
import { bandsProblem, unitProblem, weightsProblem } from './checks';

describe('advisory checks', () => {
  it('weights must add up to one within the tolerance', () => {
    expect(weightsProblem({ A: 0.25, B: 0.25, C: 0.25, D: 0.25 }, 0.0001)).toBeUndefined();
    expect(weightsProblem({ A: 0.3, B: 0.25, C: 0.25, D: 0.25 }, 0.0001)).toMatch(/add up to 1.05/);
    expect(weightsProblem({ A: 0.6, B: 0.4 }, undefined)).toBeUndefined();
    expect(weightsProblem({ A: -0.5, B: 1.5 }, 0.0001)).toMatch(/negative/);
    expect(weightsProblem(undefined, 0)).toBeUndefined();
  });

  it('bands must cover 0 to 100 once', () => {
    const ok = [{ min: 0, max: 40 }, { min: 40, max: 100 }];
    expect(bandsProblem(ok)).toBeUndefined();
    expect(bandsProblem([{ min: 0, max: 40 }, { min: 50, max: 100 }])).toMatch(/no gap/);
    expect(bandsProblem([{ min: 0, max: 60 }, { min: 40, max: 100 }])).toMatch(/no gap/);
    expect(bandsProblem([{ min: 10, max: 100 }])).toMatch(/start at 0/);
    expect(bandsProblem([{ min: 0, max: 90 }])).toMatch(/end at 100/);
    expect(bandsProblem([{ min: 40, max: 40 }])).toMatch(/start below/);
    expect(bandsProblem([{ min: 'a', max: 1 }])).toMatch(/number/);
  });

  it('a share is a value from 0 to 1', () => {
    expect(unitProblem(0.75)).toBeUndefined();
    expect(unitProblem(1.5)).toMatch(/0 to 1/);
    expect(unitProblem(-0.1)).toMatch(/0 to 1/);
  });
});
