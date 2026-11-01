import { http, HttpResponse } from 'msw';
import * as f from './fixtures';

export const ORIGIN = 'http://localhost:3000/api/v1';

/** Default happy-path handlers for every endpoint the dashboard reads; tests override single routes. */
export function dashboardHandlers() {
  return [
    http.get(`${ORIGIN}/organizations/org-1/dashboard`, () => HttpResponse.json(f.dashboard)),
    http.get(`${ORIGIN}/organizations/org-1/trends`, () => HttpResponse.json(f.trends)),
    http.get(`${ORIGIN}/scoring-runs/compare`, () => HttpResponse.json(f.comparison)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}`, () => HttpResponse.json(f.run)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/dimensions`, () => HttpResponse.json(f.dimensionsResponse)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/patterns`, () => HttpResponse.json(f.patternsResponse)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/penalties`, () => HttpResponse.json(f.penaltiesResponse)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/recommendations`, () => HttpResponse.json(f.recommendations)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/catalog`, () => HttpResponse.json(f.catalog)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/explanation`, () => HttpResponse.json(f.traceResponse)),
    http.get(`${ORIGIN}/scoring-runs/${f.RUN_ID}/answers`, () => HttpResponse.json(f.answersResponse)),
    http.get(`${ORIGIN}/scoring-runs`, () => HttpResponse.json(f.runsPage)),
  ];
}
