import { verifyToken } from '../services/sessionToken.js';

/**
 * Verifies the backend-issued session token in `Authorization: Bearer <token>`
 * (replaces Firebase ID-token verification everywhere).
 *
 * On success sets:
 *   req.session      — full token payload
 *   req.userId       — users.id (farmer/vet sessions) or null
 *   req.magistrateId — gov_magistrates.id (gov sessions) or null
 *   req.role         — 'farmer' | 'vet' | 'gov'
 *
 * 401 missing_bearer_token — no Authorization header
 * 401 invalid_session_token — bad signature / expired / wrong token type
 */
export async function verifySession(req, res, next) {
  const header = req.get('Authorization') ?? '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) {
    return res.status(401).json({
      error: 'missing_bearer_token',
      message: 'Missing Authorization: Bearer <token> header. Sign in first.',
    });
  }

  const payload = verifyToken(match[1]);
  if (!payload || payload.typ !== 'session') {
    return res.status(401).json({
      error: 'invalid_session_token',
      message: 'Session token is invalid or expired. Sign in again.',
    });
  }

  req.session = payload;
  req.role = payload.role ?? null;
  req.userId = payload.role === 'gov' ? null : payload.sub ?? null;
  req.magistrateId = payload.role === 'gov' ? payload.sub ?? null : null;
  return next();
}

/** Express middleware factory: rejects sessions whose role is not allowed. */
export function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.role || !roles.includes(req.role)) {
      return res.status(403).json({
        error: 'forbidden_role',
        message: `This action requires role: ${roles.join(' or ')}.`,
      });
    }
    return next();
  };
}
