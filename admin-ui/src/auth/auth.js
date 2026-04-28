const JWT_TOKEN_KEY = 'jwt_token';
const USERNAME_KEY = 'username';
const ROLES_KEY = 'roles';
const PERMISSIONS_KEY = 'permissions';

export function setToken(token) {
  if (!token) {
    localStorage.removeItem(JWT_TOKEN_KEY);
    return;
  }
  localStorage.setItem(JWT_TOKEN_KEY, token);
}

export function getToken() {
  return localStorage.getItem(JWT_TOKEN_KEY);
}

export function setUsername(username) {
  localStorage.setItem(USERNAME_KEY, username || '');
}

export function getUsername() {
  return localStorage.getItem(USERNAME_KEY) || '';
}

export function setRoles(roles) {
  localStorage.setItem(ROLES_KEY, JSON.stringify(roles || []));
}

export function getRoles() {
  const raw = localStorage.getItem(ROLES_KEY);
  return raw ? JSON.parse(raw) : [];
}

export function setPermissions(permissions) {
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(permissions || []));
}

export function getPermissions() {
  const raw = localStorage.getItem(PERMISSIONS_KEY);
  return raw ? JSON.parse(raw) : [];
}

export function setSessionFromMe(me) {
  setUsername(me?.username || '');
  setRoles(me?.roles || []);
  setPermissions(me?.permissions || []);
}

export function clearAuth() {
  localStorage.removeItem(JWT_TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(ROLES_KEY);
  localStorage.removeItem(PERMISSIONS_KEY);
}

export function isLoggedIn() {
  return Boolean(getToken());
}

export function getAuthHeader() {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
