import { apiClient } from './client'

export interface Role {
  id: string
  displayName: string
  isBuiltin: boolean
  sortOrder: number
  userCount: number          // how many employees have this role
}

export interface RolePermissions {
  roleId: string
  permissions: string[]
}

export const roleApi = {
  /** List all roles (with employee counts) */
  list: () => apiClient.get<Role[]>('/admin/roles').then(r => r.data),

  /** Create a custom role */
  create: (id: string, displayName: string) =>
    apiClient.post<Role>('/admin/roles', { id, displayName }).then(r => r.data),

  /** Update a role's display name */
  update: (roleId: string, displayName: string) =>
    apiClient.put(`/admin/roles/${encodeURIComponent(roleId)}`, { displayName }).then(r => r.data),

  /** Delete a custom role (only if no employees assigned) */
  delete: (roleId: string) =>
    apiClient.delete(`/admin/roles/${encodeURIComponent(roleId)}`).then(r => r.data),

  /** Get a role's permission keys */
  getPermissions: (roleId: string) =>
    apiClient.get<RolePermissions>(`/admin/roles/${encodeURIComponent(roleId)}/permissions`)
      .then(r => r.data),

  /** Replace a role's permissions */
  updatePermissions: (roleId: string, permissions: string[]) =>
    apiClient.put(`/admin/roles/${encodeURIComponent(roleId)}/permissions`, { permissions })
      .then(r => r.data),
}
