import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/app/providers/auth-context';
import { configurationApi } from '@/services/api/endpoints';
import { isPlatformAdmin } from '@/services/auth/principal';
import type { ConfigApproveRequest, ConfigFixtureSpec } from '@/types/api';
import { queryKeys } from './keys';

/** The configuration endpoints for whoever is signed in: the platform default for the platform administrator, the tenant otherwise. */
export function useConfigApi() {
  const { principal } = useAuth();
  const platform = isPlatformAdmin(principal);
  return useMemo(() => ({ platform, api: configurationApi(platform) }), [platform]);
}

export function useConfigLayers() {
  const { platform, api } = useConfigApi();
  return useQuery({ queryKey: queryKeys.configLayers(platform), queryFn: ({ signal }) => api.layers(signal) });
}

/** The versions of one layer, newest first. A tenant layer is TENANT or ORGANIZATION with an organization id; the platform has one layer. */
export function useConfigVersions(scope: string, organizationId = '') {
  const { platform, api } = useConfigApi();
  return useQuery({
    queryKey: queryKeys.configVersions(platform, scope, organizationId),
    queryFn: ({ signal }) => api.versions(scope, organizationId || undefined, signal),
  });
}

export function useConfigVersion(id: string | undefined) {
  const { platform, api } = useConfigApi();
  return useQuery({ queryKey: queryKeys.configVersion(platform, id ?? ''), queryFn: ({ signal }) => api.version(id as string, signal), enabled: !!id });
}

export function useConfigFixtures(layerId: string | undefined) {
  const { platform, api } = useConfigApi();
  return useQuery({ queryKey: queryKeys.configFixtures(platform, layerId ?? ''), queryFn: ({ signal }) => api.fixtures(layerId as string, signal), enabled: !!layerId });
}

type Api = ReturnType<typeof configurationApi>;

/** A change to configuration changes every list and version it appears in, so they are read again afterwards. */
function useConfigMutation<V, R>(run: (api: Api, variables: V) => Promise<R>, refresh = true) {
  const { api } = useConfigApi();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (variables: V) => run(api, variables),
    onSuccess: () => (refresh ? client.invalidateQueries({ queryKey: queryKeys.configurationAll }) : undefined),
  });
}

export const useCreateConfigDraft = () => useConfigMutation((api, v: { scope?: string; organizationId?: string; document?: unknown }) => api.createDraft(v));
export const useUpdateConfigDraft = () => useConfigMutation((api, v: { id: string; document: unknown }) => api.updateDraft(v.id, v.document));
export const useCopyConfigVersion = () => useConfigMutation((api, id: string) => api.copy(id));
/** Checking and previewing read the draft as it was last saved and change nothing, so they refresh nothing. */
export const useValidateConfigVersion = () => useConfigMutation((api, id: string) => api.validate(id), false);
export const usePreviewConfigVersion = () => useConfigMutation((api, id: string) => api.preview(id), false);
export const useSubmitConfigVersion = () => useConfigMutation((api, id: string) => api.submit(id));
export const useApproveConfigVersion = () => useConfigMutation((api, v: { id: string; request: ConfigApproveRequest }) => api.approve(v.id, v.request));
export const useRejectConfigVersion = () => useConfigMutation((api, v: { id: string; comment: string }) => api.reject(v.id, v.comment));
export const usePauseConfigVersion = () => useConfigMutation((api, id: string) => api.pause(id));
export const useResumeConfigVersion = () => useConfigMutation((api, id: string) => api.resume(id));
export const useArchiveConfigVersion = () => useConfigMutation((api, id: string) => api.archive(id));
export const useAddConfigFixture = () => useConfigMutation((api, v: { layerId: string; spec: ConfigFixtureSpec }) => api.addFixture(v.layerId, v.spec));
export const useReplaceConfigFixture = () => useConfigMutation((api, v: { layerId: string; id: string; spec: ConfigFixtureSpec }) => api.replaceFixture(v.layerId, v.id, v.spec));
export const useDeleteConfigFixture = () => useConfigMutation((api, v: { layerId: string; id: string }) => api.deleteFixture(v.layerId, v.id));
