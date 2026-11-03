import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as f from '@/tests/fixtures';
import { ORIGIN } from '@/tests/msw';
import { installMockApi } from '@/tests/pages';
import { renderRoutes } from '@/tests/utils';
import { ReportsPage } from './ReportsPage';

const server = installMockApi();
const routes = [{ path: '/reports', element: <ReportsPage /> }];
const route = `/reports?type=LEADERSHIP_INTELLIGENCE&run=${f.RUN_ID}`;

const report = {
  type: 'LEADERSHIP_INTELLIGENCE',
  title: 'Leadership Intelligence Report',
  redacted: false,
  notice: 'The Leadership Intelligence Score is an organizational diagnostic signal.',
  sections: [
    { id: 'summary', heading: 'Summary', note: null, columns: ['Item', 'Value'], rows: [['Organization', 'org-1'], ['Leadership Intelligence Score', '67']] },
    { id: 'patterns', heading: 'Detected patterns', note: 'No pattern was detected.', columns: ['Pattern', 'Id'], rows: [] },
  ],
};

const reportRoute = () => http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, () => HttpResponse.json(report));

describe('ReportsPage', () => {
  it('asks for a report and an assessment when none is chosen', async () => {
    renderRoutes(routes, { route: '/reports' });
    expect(await screen.findByText('Choose a report')).toBeInTheDocument();
    expect(screen.getByLabelText('Report')).toHaveValue('LEADERSHIP_INTELLIGENCE');
    expect(screen.queryByLabelText('Earlier assessment')).not.toBeInTheDocument();
  });

  it('draws the report the backend built, section by section, with the notice', async () => {
    server.use(reportRoute());
    renderRoutes(routes, { route });
    expect(await screen.findByRole('heading', { name: 'Leadership Intelligence Report' })).toBeInTheDocument();
    expect(screen.getByText(/organizational diagnostic signal/)).toBeInTheDocument();
    const summary = screen.getByRole('table', { name: 'Summary' });
    expect(within(summary).getByRole('row', { name: /Leadership Intelligence Score/ })).toHaveTextContent('67');
    // a section with no rows says why instead of drawing an empty table
    expect(screen.getByText('No pattern was detected.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Detected patterns' })).not.toBeInTheDocument();
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('says when values are withheld for the role', async () => {
    server.use(http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, () => HttpResponse.json({ ...report, redacted: true })));
    renderRoutes(routes, { route });
    expect(await screen.findByRole('note')).toHaveTextContent('Some values are withheld for your role.');
  });

  it('shows the error of a report that cannot be built, with a retry', async () => {
    server.use(http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, () => HttpResponse.json({ code: 'SCORING_EXECUTION_FAILED', message: 'x', details: {} }, { status: 500 })));
    renderRoutes(routes, { route });
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('refuses a response that is not a report', async () => {
    server.use(http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, () => HttpResponse.json({ nope: true })));
    renderRoutes(routes, { route });
    expect(await screen.findByText(/unexpected format/)).toBeInTheDocument();
  });

  it('needs an assessment before it shows a report', async () => {
    renderRoutes(routes, { route: '/reports' });
    await userEvent.click(await screen.findByRole('button', { name: 'Show report' }));
    expect(await screen.findByText('Choose an assessment')).toBeInTheDocument();
  });

  it('shows the report chosen in the form', async () => {
    const { router } = renderRoutes(routes, { route: '/reports' });
    await screen.findAllByRole('option', { name: /62\.50/ });
    await userEvent.selectOptions(screen.getByLabelText('Report'), 'PATTERN_PENALTY');
    await userEvent.selectOptions(screen.getByLabelText('Assessment'), f.RUN_ID);
    await userEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(router.state.location.search).toBe(`?type=PATTERN_PENALTY&run=${f.RUN_ID}`);
  });

  it('asks for two different assessments for the comparison', async () => {
    const { router } = renderRoutes(routes, { route: '/reports' });
    await screen.findAllByRole('option', { name: /62\.50/ });
    await userEvent.selectOptions(screen.getByLabelText('Report'), 'HISTORICAL_COMPARISON');
    expect(screen.getByLabelText('Earlier assessment')).toBeInTheDocument();
    expect(screen.getByLabelText('Later assessment')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByText('Choose the earlier assessment')).toBeInTheDocument();
    expect(screen.getByText('Choose the later assessment')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Earlier assessment'), f.RUN_ID);
    await userEvent.selectOptions(screen.getByLabelText('Later assessment'), f.RUN_ID);
    await userEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByText('Choose two different assessments')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Earlier assessment'), f.PREVIOUS_RUN_ID);
    await userEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(router.state.location.search).toBe(`?type=HISTORICAL_COMPARISON&run=${f.RUN_ID}&base=${f.PREVIOUS_RUN_ID}`);
  });

  it('asks the backend for the comparison with both runs', async () => {
    let seen: URL | undefined;
    server.use(http.get(`${ORIGIN}/reports/HISTORICAL_COMPARISON`, ({ request }) => {
      seen = new URL(request.url);
      return HttpResponse.json({ ...report, type: 'HISTORICAL_COMPARISON', title: 'Historical Comparison Report' });
    }));
    renderRoutes(routes, { route: `/reports?type=HISTORICAL_COMPARISON&run=${f.RUN_ID}&base=${f.PREVIOUS_RUN_ID}` });
    expect(await screen.findByRole('heading', { name: 'Historical Comparison Report' })).toBeInTheDocument();
    expect(seen?.searchParams.get('baseRunId')).toBe(f.PREVIOUS_RUN_ID);
    expect(seen?.searchParams.get('runId')).toBe(f.RUN_ID);
  });
});

describe('ReportsPage downloads', () => {
  let click: ReturnType<typeof vi.spyOn>;
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:report');
    URL.revokeObjectURL = vi.fn();
    click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });
  afterEach(() => click.mockRestore());

  it('saves the PDF under the name the server gave it', async () => {
    let accept: string | null = null;
    let query: URL | undefined;
    server.use(
      http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, ({ request }) => {
        const url = new URL(request.url);
        if (url.searchParams.get('format') !== 'pdf') return HttpResponse.json(report);
        accept = request.headers.get('Accept');
        query = url;
        return new HttpResponse('%PDF-1.5', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="leadership-intelligence-run.pdf"' } });
      }),
    );
    renderRoutes(routes, { route });
    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Saved leadership-intelligence-run.pdf');
    expect(accept).toBe('application/pdf');
    expect(query?.searchParams.get('runId')).toBe(f.RUN_ID);
    expect(click).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report');
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeEnabled();
  });

  it('saves the CSV as a file too', async () => {
    server.use(http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, ({ request }) => {
      if (new URL(request.url).searchParams.get('format') !== 'csv') return HttpResponse.json(report);
      return new HttpResponse('Item,Value', { headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="leadership-intelligence-run.csv"' } });
    }));
    renderRoutes(routes, { route });
    await userEvent.click(await screen.findByRole('button', { name: 'Download CSV' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Saved leadership-intelligence-run.csv'));
  });

  it('says so when the file cannot be created, and lets the user try again', async () => {
    server.use(http.get(`${ORIGIN}/reports/LEADERSHIP_INTELLIGENCE`, ({ request }) => {
      if (new URL(request.url).searchParams.get('format') !== 'pdf') return HttpResponse.json(report);
      return HttpResponse.json({ code: 'SCORING_EXECUTION_FAILED', message: 'x', details: {} }, { status: 500 });
    }));
    renderRoutes(routes, { route });
    await userEvent.click(await screen.findByRole('button', { name: 'Download PDF' }));
    await waitFor(() => expect(screen.getByText('The PDF file could not be created. Try again.')).toBeInTheDocument());
    expect(click).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeEnabled();
  });
});
