import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { identityApi } from '@/services/api/identity';
import type { CreateApiTokenRequest, InviteMemberRequest, SecurityAuditParams, UpdateMemberRequest } from '@/types/identity';
import { queryKeys } from './keys';

// ---------------- members (decision D72) ----------------

export const useMembers = (enabled = true) => useQuery({ queryKey: queryKeys.members, queryFn: ({ signal }) => identityApi.members(signal), enabled });

/** Any change to a member changes the list. */
function useMemberMutation<V, R>(fn: (v: V) => Promise<R>) {
  const client = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.members }) });
}

export const useInviteMember = () => useMemberMutation((request: InviteMemberRequest) => identityApi.inviteMember(request));
export const useUpdateMember = () => useMemberMutation((v: { id: string; request: UpdateMemberRequest }) => identityApi.updateMember(v.id, v.request));
export const useRemoveMember = () => useMemberMutation((id: string) => identityApi.removeMember(id));
export const useResendInvitation = () => useMemberMutation((id: string) => identityApi.resendInvitation(id));
export const useInvitePerson = () => useMemberMutation((personId: string) => identityApi.invitePerson(personId));

// ---------------- API tokens (decision D70) ----------------

export const useApiTokens = (enabled = true) => useQuery({ queryKey: queryKeys.apiTokens, queryFn: ({ signal }) => identityApi.apiTokens(signal), enabled });

export function useCreateApiToken() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateApiTokenRequest) => identityApi.createApiToken(request),
    // the answer holds the secret and goes to the page that asked, never into the cache
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.apiTokens }),
  });
}

export function useRevokeApiToken() {
  const client = useQueryClient();
  return useMutation({ mutationFn: (id: string) => identityApi.revokeApiToken(id), onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.apiTokens }) });
}

// ---------------- security audit (decision D74) ----------------

export const useSecurityAudit = (platform: boolean, params: SecurityAuditParams) =>
  useQuery({
    queryKey: queryKeys.securityAudit(platform, params),
    queryFn: ({ signal }) => (platform ? identityApi.platformSecurityAudit(params, signal) : identityApi.securityAudit(params, signal)),
    placeholderData: keepPreviousData,
  });
