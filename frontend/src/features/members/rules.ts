// Who may manage whom (decision D72), mirrored for the interface so it does not offer what the server would refuse. The server decides.

/** True when someone with these roles may give a membership this role: an owner any, an administrator all but owner and administrator. */
export function mayGrantRole(actorRoles: string[], role: string): boolean {
  if (actorRoles.includes('OWNER')) return true;
  return actorRoles.includes('ADMIN') && role !== 'OWNER' && role !== 'ADMIN';
}

/** True when someone with these roles may change, suspend or remove a member who holds these roles. */
export function mayManageRoles(actorRoles: string[], targetRoles: string[]): boolean {
  return targetRoles.every((role) => mayGrantRole(actorRoles, role));
}

/** Adds or removes a role; a respondent stands alone, so choosing it clears the others and choosing another clears it. */
export function toggleRole(chosen: string[], role: string): string[] {
  if (chosen.includes(role)) return chosen.filter((r) => r !== role);
  if (role === 'RESPONDENT') return ['RESPONDENT'];
  return [...chosen.filter((r) => r !== 'RESPONDENT'), role];
}

/** "org-1, org-2" to ["org-1", "org-2"]: commas or spaces, no empties, no repeats. */
export function parseList(text: string): string[] {
  return [...new Set(text.split(/[\s,]+/).map((s) => s.trim()).filter((s) => s.length > 0))];
}
