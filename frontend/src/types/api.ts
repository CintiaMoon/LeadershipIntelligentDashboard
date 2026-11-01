import type { components } from './api.d';

// Generated from the backend OpenAPI document (npm run gen:api). The generator marks every field optional, so view
// code treats absent values as "not provided" and never as zero.
type S = components['schemas'];

export type ScoringResult = S['ScoringResultResponse'];
export type DimensionResult = S['DimensionResponse'];
export type RunSummary = S['RunSummaryResponse'];
export type AssessmentSummary = S['AssessmentResponse'];
export type RunPage = S['PageResponseRunSummaryResponse'];
export type AssessmentPage = S['PageResponseAssessmentResponse'];
export type Dashboard = S['DashboardResponse'];
export type Trends = S['TrendsResponse'];
export type TrendPoint = S['TrendPointResponse'];
export type Comparison = S['ComparisonResponse'];
export type RunCatalog = S['RunCatalog'];
export type Recommendations = S['RecommendationsResponse'];
export type Recommendation = S['Recommendation'];
export type RunDimensions = S['RunDimensionsResponse'];
export type RunPatterns = S['RunPatternsResponse'];
export type RunPenalties = S['RunPenaltiesResponse'];
export type RunImbalances = S['RunImbalancesResponse'];
export type Explanation = S['ExplanationResponse'];
export type RunAnswers = S['RunAnswersResponse'];
export type CalculateRequest = S['CalculateRequest'];
export type Tenant = S['TenantResponse'];
export type TenantList = S['TenantListResponse'];
export type CreateTenantRequest = S['CreateTenantRequest'];
export type RecalculateRequest = S['RecalculateRequest'];

/** The standard backend error body (specv3.md 28.10). */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, string>;
  correlationId?: string;
}
