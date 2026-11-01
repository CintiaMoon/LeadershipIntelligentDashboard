import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeNames } from '@/features/dashboard/display';
import { catalog } from '@/tests/fixtures';
import { EvidenceList } from './EvidenceList';

const names = makeNames(catalog);

describe('EvidenceList', () => {
  it('shows a boolean signal as present or absent and a number with its scale', () => {
    render(
      <EvidenceList
        names={names}
        redacted={false}
        evidence={[
          { kind: 'SIGNAL', operandKey: 'signal:SINGLE_POINT_OF_FAILURE', flag: true, reliability: 0.8, numericValue: undefined, sourceQuestionIds: ['Q04'] },
          { kind: 'SIGNAL', operandKey: 'signal:WORKLOAD_PRESSURE', numericValue: 80, reliability: 1, sourceQuestionIds: ['Q05'] },
        ]}
      />,
    );
    expect(screen.getByText('Present')).toBeInTheDocument();
    expect(screen.getByText('80.00')).toBeInTheDocument();
    expect(screen.getByText('Reliability 80%')).toBeInTheDocument();
  });

  it('says so when no evidence was recorded', () => {
    render(<EvidenceList names={names} redacted={false} evidence={[]} />);
    expect(screen.getByText('No evidence items were recorded.')).toBeInTheDocument();
  });
});
